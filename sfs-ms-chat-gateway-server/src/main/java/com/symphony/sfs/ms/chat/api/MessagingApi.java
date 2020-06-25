package com.symphony.sfs.ms.chat.api;

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
import com.symphony.sfs.ms.chat.util.SymphonyUserUtils;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.security.StaticSessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamInfo;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Date;

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

  @Override
  public ResponseEntity<SendMessageResponse> sendMessage(SendMessageRequest request) {
    final FederatedAccount federatedAccount = federatedAccountRepository.findBySymphonyId(request.getFromSymphonyUserId()).orElseThrow(FederatedAccountNotFoundProblem::new);

    if (!isEveryoneEntitled(request, federatedAccount)) {
      // TODO CES-1164: Advisor remediation huge kludge to let messageId and symphonyUserId mandatory in sendMessage API input
      // Those values will be tested explicitely in Blades
      String fakeMessageId = "fakeMessageId";
      String fakeFromSymphonyUser = "66613";

      // We do not have messageId and fromSymphonyUser here
      // This specific case has to be handled in the the EMPs
      empClient.sendMessage(federatedAccount.getEmp(), request.getStreamId(), fakeMessageId, SymphonyUserUtils.newIUser(fakeFromSymphonyUser), federatedAccount, new Date().getTime(), "Sorry, your contact is no longer available", null);

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
   * @param request
   * @return Optional with emp if everyone is entitled otherwise Optional.empty()
   */
  private boolean isEveryoneEntitled(SendMessageRequest request, FederatedAccount fromFederatedAccount) {

    String emp = fromFederatedAccount.getEmp();
    String fromSymphonyId = fromFederatedAccount.getSymphonyUserId();

    SymphonySession botSession = authenticationService.authenticate(
      podConfiguration.getSessionAuth(),
      podConfiguration.getKeyAuth(),
      botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());

    StreamInfo streamInfo = streamService.getStreamInfo(podConfiguration.getUrl(), new StaticSessionSupplier<>(botSession), request.getStreamId()).orElseThrow(CannotRetrieveStreamIdProblem::new);

    // ALL non-Federated users must be entitled (advisors)
    return streamInfo.getStreamAttributes().getMembers()
      .parallelStream()
      .map(String::valueOf)
      .filter(symphonyId -> !symphonyId.equals(fromSymphonyId)) // Remove federatedAccountUser
      .allMatch(symphonyId -> adminClient.getEntitlementAccess(symphonyId, emp).isPresent());
  }

}
