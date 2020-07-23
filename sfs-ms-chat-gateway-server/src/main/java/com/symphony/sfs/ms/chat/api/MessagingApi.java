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
import org.springframework.cloud.sleuth.annotation.ContinueSpan;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.UserInfo;
import org.apache.log4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.zalando.problem.violations.Violation;

import javax.annotation.PostConstruct;
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
@RequiredArgsConstructor
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

  private final MeterManager meterManager;

  private Counter messagesSent;
  private Counter messagesBlocked;

  @PostConstruct
  public void initializeMetrics() {
    messagesSent = meterManager.register(Counter.builder("sent.messages.to.symphony"));
    messagesBlocked = meterManager.register(Counter.builder("blocked.messages.to.symphony"));
  }

  @Override
  @ContinueSpan
  public ResponseEntity<SendMessageResponse> sendMessage(SendMessageRequest request) {
    MDC.put("streamId", request.getStreamId());
    FederatedAccount federatedAccount = federatedAccountRepository.findBySymphonyId(request.getFromSymphonyUserId()).orElseThrow(FederatedAccountNotFoundProblem::new);
    MDC.put("federatedUserId", federatedAccount.getFederatedUserId());
    Optional<String> advisorSymphonyUserId = findAdvisor(request.getStreamId(), federatedAccount.getSymphonyUserId());
    Optional<String> notEntitled = notEntitledMessage(advisorSymphonyUserId, federatedAccount.getEmp());
    advisorSymphonyUserId.ifPresent(s -> MDC.put("advisor", s));

    String symphonyMessageId = null;
    if (notEntitled.isPresent()) {
      messagesBlocked.increment();
      blockIncomingMessage(federatedAccount.getEmp(), request.getStreamId(), notEntitled.get());
    } else {
      messagesSent.increment();
      symphonyMessageId = forwardIncomingMessageToSymphony(request).orElseThrow(SendMessageFailedProblem::new);
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
  private Optional<String> forwardIncomingMessageToSymphony(SendMessageRequest request) {
    LOG.info("incoming message");
    String messageContent = "<messageML>" + request.getText() + "</messageML>";
    if (request.getFormatting() == null) {
      return symphonyMessageSender.sendRawMessage(request.getStreamId(), request.getFromSymphonyUserId(), messageContent);
    }

    messageContent = request.getText();
    switch (request.getFormatting()) {
      case SIMPLE:
        return symphonyMessageSender.sendSimpleMessage(request.getStreamId(), request.getFromSymphonyUserId(), messageContent);
      case NOTIFICATION:
        return symphonyMessageSender.sendNotificationMessage(request.getStreamId(), request.getFromSymphonyUserId(), messageContent);
      case INFO:
        return symphonyMessageSender.sendInfoMessage(request.getStreamId(), request.getFromSymphonyUserId(), messageContent);
      case ALERT:
        return symphonyMessageSender.sendAlertMessage(request.getStreamId(), request.getFromSymphonyUserId(), messageContent);
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
}
