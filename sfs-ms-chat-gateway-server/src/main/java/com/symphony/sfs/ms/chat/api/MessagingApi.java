package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.admin.generated.model.EntitlementResponse;
import com.symphony.sfs.ms.chat.generated.model.CannotRetrieveStreamIdProblem;
import com.symphony.sfs.ms.chat.generated.model.FederatedAccountNotFoundProblem;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessagesRequest;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessagesResponse;
import com.symphony.sfs.ms.chat.generated.model.SendMessageRequest;
import com.symphony.sfs.ms.chat.generated.model.SendMessageResponse;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.SymphonyMessageService;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageRequest;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.security.StaticSessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamInfo;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.symphony.user.UsersInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.UserInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

// TODO full refactor, too much code in this API class

@Slf4j
@RestController
@RequiredArgsConstructor
public class MessagingApi implements com.symphony.sfs.ms.chat.generated.api.MessagingApi {

  private final SymphonyMessageService symphonyMessageService;
  private final StreamService streamService;
  private final PodConfiguration podConfiguration;
  private final BotConfiguration botConfiguration;
  private final FederatedAccountRepository federatedAccountRepository;
  private final AuthenticationService authenticationService;
  private final AdminClient adminClient;
  private final EmpClient empClient;
  private final UsersInfoService usersInfoService;

  @Override
  public ResponseEntity<SendMessageResponse> sendMessage(SendMessageRequest request) {
    final FederatedAccount federatedAccount = federatedAccountRepository.findBySymphonyId(request.getFromSymphonyUserId()).orElseThrow(FederatedAccountNotFoundProblem::new);

    // Assume IM --> only 1 advisor expected
    Optional<String> advisorSymphonyUserId = findAdvisor(request.getStreamId(), federatedAccount);
    if (!advisorSymphonyUserId.isPresent()) {
      SymphonySession botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());
      final List<UserInfo> usersFromIds = usersInfoService.getUsersFromIds(podConfiguration.getUrl(), new StaticSessionSupplier<>(botSession), Collections.singletonList(advisorSymphonyUserId.get()));
      String message;
      if (usersFromIds.isEmpty()) {
        LOG.warn("Advisor with symphonyUserId {} not found on Symphony side", advisorSymphonyUserId.get());
        message = "Sorry, your contact is no longer available";
      } else {
        message = String.format("Sorry, your contact %s is no longer available", usersFromIds.get(0).getDisplayName());
      }
      empClient.sendSystemMessage(federatedAccount.getEmp(), request.getStreamId(), new Date().getTime(), message, SendSystemMessageRequest.TypeEnum.ALERT);
    } else {
      String messageContent = "<messageML>" + request.getText() + "</messageML>";
      if (request.getFormatting() == null) {
        symphonyMessageService.sendRawMessage(request.getStreamId(), request.getFromSymphonyUserId(), messageContent);
      } else {
        messageContent = request.getText();
        switch (request.getFormatting()) {
          case SIMPLE:
            symphonyMessageService.sendSimpleMessage(request.getStreamId(), request.getFromSymphonyUserId(), messageContent);
            break;
          case NOTIFICATION:
            symphonyMessageService.sendNotificationMessage(request.getStreamId(), request.getFromSymphonyUserId(), messageContent);
            break;
          case INFO:
            symphonyMessageService.sendInfoMessage(request.getStreamId(), request.getFromSymphonyUserId(), messageContent);
            break;
          case ALERT:
            symphonyMessageService.sendAlertMessage(request.getStreamId(), request.getFromSymphonyUserId(), messageContent);
            break;
        }
      }
    }

    SendMessageResponse response = new SendMessageResponse();
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<RetrieveMessagesResponse> retrieveMessages(@Valid RetrieveMessagesRequest body) {
    RetrieveMessagesResponse response = symphonyMessageService.retrieveMessages(body.getMessagesIds(), body.getSymphonyUserId());
    return ResponseEntity.ok(response);
  }

  /**
   * Pre-requisite: the stream is an IM between an advisor and an emp user
   *
   * @param streamId
   * @param fromFederatedAccount
   * @return Optional with found advisor symphonyUserId. Empty if no advisor found
   */
  /**
   *
   * @return
   */
  private Optional<String> findAdvisor(String streamId, FederatedAccount fromFederatedAccount) {

    String emp = fromFederatedAccount.getEmp();
    String fromSymphonyId = fromFederatedAccount.getSymphonyUserId();

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
      Optional<String> advisor = streamInfo.getStreamAttributes().getMembers()
        .parallelStream()
        .map(String::valueOf)
        .filter(symphonyId -> !symphonyId.equals(fromSymphonyId)) // Remove federatedAccountUser
        .findFirst();

      if (advisor.isPresent()) {
        return adminClient.getEntitlementAccess(advisor.get(), emp).map(EntitlementResponse::getSymphonyId);
      } else {
        LOG.error("Stream {} has only an EMP user", streamId);
        return Optional.empty();
      }
    }
  }

}
