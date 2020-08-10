package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.chat.generated.model.CannotRetrieveStreamIdProblem;
import com.symphony.sfs.ms.chat.generated.model.FederatedAccountNotFoundProblem;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessagesRequest;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessagesResponse;
import com.symphony.sfs.ms.chat.generated.model.SendMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.SendMessageRequest;
import com.symphony.sfs.ms.chat.generated.model.SendMessageResponse;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.SymphonyMessageSender;
import com.symphony.sfs.ms.chat.service.SymphonyMessageService;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageRequest;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.health.MeterManager;
import com.symphony.sfs.ms.starter.security.StaticSessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamInfo;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.symphony.user.UsersInfoService;
import io.micrometer.core.instrument.Counter;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.UserInfo;
import org.apache.log4j.MDC;
import org.springframework.cloud.sleuth.annotation.ContinueSpan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.zalando.problem.violations.Violation;

import javax.validation.Valid;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.symphony.sfs.ms.starter.util.ProblemUtils.newConstraintViolation;

// TODO full refactor, too much code in this API class

@Slf4j
@RestController
public class MessagingApi implements com.symphony.sfs.ms.chat.generated.api.MessagingApi {

  private final SymphonyMessageSender symphonyMessageSender;
  private final SymphonyMessageService symphonyMessageService;
  private final StreamService streamService;
  private final PodConfiguration podConfiguration;
  private final BotConfiguration botConfiguration;
  private final FederatedAccountRepository federatedAccountRepository;
  private final AuthenticationService authenticationService;
  private final AdminClient adminClient;
  private final EmpClient empClient;
  private final UsersInfoService usersInfoService;

  private final MessagesMetrics messagesMetrics;

  public MessagingApi(SymphonyMessageSender symphonyMessageSender, SymphonyMessageService symphonyMessageService, StreamService streamService, PodConfiguration podConfiguration, BotConfiguration botConfiguration, FederatedAccountRepository federatedAccountRepository, AuthenticationService authenticationService, AdminClient adminClient, EmpClient empClient, UsersInfoService usersInfoService, MeterManager meterManager) {
    this.symphonyMessageSender = symphonyMessageSender;
    this.symphonyMessageService = symphonyMessageService;
    this.streamService = streamService;
    this.podConfiguration = podConfiguration;
    this.botConfiguration = botConfiguration;
    this.federatedAccountRepository = federatedAccountRepository;
    this.authenticationService = authenticationService;
    this.adminClient = adminClient;
    this.empClient = empClient;
    this.usersInfoService = usersInfoService;
    this.messagesMetrics = new MessagesMetrics(meterManager);
  }

  @Override
  @ContinueSpan
  public ResponseEntity<SendMessageResponse> sendMessage(SendMessageRequest request) {
    MDC.put("streamId", request.getStreamId());
    FederatedAccount federatedAccount;
    Optional<FederatedAccount> optionalFederatedAccount = federatedAccountRepository.findBySymphonyId(request.getFromSymphonyUserId());
    if (optionalFederatedAccount.isPresent()) {
      federatedAccount = optionalFederatedAccount.get();
    } else {
      messagesMetrics.onBlockMessage(BlockingCause.FEDERATED_ACCOUNT_NOT_FOUND);
      throw new FederatedAccountNotFoundProblem();
    }
    MDC.put("federatedUserId", federatedAccount.getFederatedUserId());

    Optional<String> advisorSymphonyUserId = findAdvisor(request.getStreamId(), federatedAccount.getSymphonyUserId());
    Optional<String> notEntitled = notEntitledMessage(advisorSymphonyUserId, federatedAccount.getEmp());
    advisorSymphonyUserId.ifPresent(s -> MDC.put("advisor", s));

    String symphonyMessageId = null;
    if (notEntitled.isPresent()) {
      messagesMetrics.onBlockMessage(BlockingCause.ADVISOR_NO_LONGER_AVAILABLE);
      blockIncomingMessage(federatedAccount.getEmp(), request.getStreamId(), notEntitled.get());
    } else {
      symphonyMessageId = forwardIncomingMessageToSymphony(request, advisorSymphonyUserId.get()).orElseThrow(SendMessageFailedProblem::new);
    }

    return ResponseEntity.ok(new SendMessageResponse().id(symphonyMessageId));
  }

  @Override
  @ContinueSpan
  public ResponseEntity<RetrieveMessagesResponse> retrieveMessages(@Valid RetrieveMessagesRequest body) {
    RetrieveMessagesResponse response = symphonyMessageService.retrieveMessages(body.getMessagesIds(), body.getSymphonyUserId());
    return ResponseEntity.ok(response);
  }

  /**
   * Pre-requisite: the stream is an IM between an advisor and an emp user
   *
   * @param streamId
   * @param fromFederatedAccountSymphonyUserId
   * @return Optional with found advisor symphonyUserId. Empty if no advisor found
   */
  private Optional<String> findAdvisor(String streamId, String fromFederatedAccountSymphonyUserId) {

    SymphonySession botSession = authenticationService.authenticate(
      podConfiguration.getSessionAuth(),
      podConfiguration.getKeyAuth(),
      botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());

    StreamInfo streamInfo = streamService.getStreamInfo(podConfiguration.getUrl(), new StaticSessionSupplier<>(botSession), streamId).orElseThrow(CannotRetrieveStreamIdProblem::new);

    List<Long> streamMemberIds = streamInfo.getStreamAttributes().getMembers();
    if (streamMemberIds.size() != 2) {
      LOG.warn("Unsupported stream {} with {} members (symphonyUserIds = {})", streamId, streamMemberIds.size(), streamMemberIds);
      return Optional.empty();
    } else {

      // Nominal case of IM: We have a stream with exactly 2 members
      // - 1 user must an advisor
      // - 1 user must be a emp user
      return streamInfo.getStreamAttributes().getMembers()
        .parallelStream()
        .map(String::valueOf)
        .filter(symphonyId -> !symphonyId.equals(fromFederatedAccountSymphonyUserId)) // Remove federatedAccountUser
        .findFirst();
    }
  }

  /**
   * @param advisorSymphonyUserId
   * @param emp
   * @return If present, message indicating that advisor is NOT entitled.
   */
  private Optional<String> notEntitledMessage(Optional<String> advisorSymphonyUserId, String emp) {
    final String CONTACT_NOT_AVAILABLE = "Sorry, your contact is no longer available";
    final String CONTACT_WITH_DETAILS_NOT_AVAILABLE = "Sorry, your contact %s is no longer available";

    // No advisor at all
    if (advisorSymphonyUserId.isEmpty()) {
      return Optional.of(CONTACT_NOT_AVAILABLE);
    }

    // Advisor entitled
    if (adminClient.getEntitlementAccess(advisorSymphonyUserId.get(), emp).isPresent()) {
      return Optional.empty();
    }

    // From here advisor is not entitled
    // Get advisor details
    SymphonySession botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());
    final List<UserInfo> usersFromIds = usersInfoService.getUsersFromIds(podConfiguration.getUrl(), new StaticSessionSupplier<>(botSession), Collections.singletonList(advisorSymphonyUserId.get()));

    String message;
    if (usersFromIds.isEmpty()) {
      LOG.warn("Advisor with symphonyUserId {} not found on Symphony side", advisorSymphonyUserId.get());
      message = CONTACT_NOT_AVAILABLE;
    } else {
      // Message with advisor details
      message = String.format(CONTACT_WITH_DETAILS_NOT_AVAILABLE, usersFromIds.get(0).getDisplayName());
    }

    return Optional.ofNullable(message);
  }

  /**
   * @param request
   */
  private Optional<String> forwardIncomingMessageToSymphony(SendMessageRequest request, String toSymphonyUserId) {
    LOG.info("incoming message");
    String messageContent = "<messageML>" + request.getText() + "</messageML>";
    if (request.getFormatting() == null) {
      return symphonyMessageSender.sendRawMessage(request.getStreamId(), request.getFromSymphonyUserId(), messageContent, toSymphonyUserId);
    }

    messageContent = request.getText();
    switch (request.getFormatting()) {
      case SIMPLE:
        return symphonyMessageSender.sendSimpleMessage(request.getStreamId(), request.getFromSymphonyUserId(), messageContent, toSymphonyUserId);
      case NOTIFICATION:
        return symphonyMessageSender.sendNotificationMessage(request.getStreamId(), request.getFromSymphonyUserId(), messageContent, toSymphonyUserId);
      case INFO:
        return symphonyMessageSender.sendInfoMessage(request.getStreamId(), request.getFromSymphonyUserId(), messageContent, toSymphonyUserId);
      case ALERT:
        return symphonyMessageSender.sendAlertMessage(request.getStreamId(), request.getFromSymphonyUserId(), messageContent, toSymphonyUserId);
      default:
        throw newConstraintViolation(new Violation("formatting", "invalid type, must be one of " + Arrays.toString(SendMessageRequest.FormattingEnum.values())));
    }
  }

  /**
   * @param emp
   * @param streamId
   * @param reasonText
   */
  private void blockIncomingMessage(String emp, String streamId, String reasonText) {
    LOG.info("block incoming message");
    empClient.sendSystemMessage(emp, streamId, new Date().getTime(), reasonText, SendSystemMessageRequest.TypeEnum.ALERT);
  }

  @AllArgsConstructor
  private enum BlockingCause {
    FEDERATED_ACCOUNT_NOT_FOUND("federated account not found"),
    ADVISOR_NO_LONGER_AVAILABLE("advisor no longer available");

    private String blockingCause;
  }

  @RequiredArgsConstructor
  private static class MessagesMetrics {
    private final MeterManager meterManager;

    public void onBlockMessage(BlockingCause blockingCause) {
      meterManager.register(Counter.builder("blocked.messages.to.symphony").tag("cause", blockingCause.blockingCause)).increment();
    }
  }
}
