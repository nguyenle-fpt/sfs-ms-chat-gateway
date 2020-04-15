package com.symphony.sfs.ms.chat.service;

import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.generated.model.SendMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.SymphonySendMessageFailedProblem;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.UserSession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SymphonyMessageService {

  private final PodConfiguration podConfiguration;
  private final ChatConfiguration chatConfiguration;
  private final AuthenticationService authenticationService;
  private final FederatedAccountRepository federatedAccountRepository;
  private final StreamService streamService;

  public static final String USER_WAITING_CONFIRMATION = "user.waiting.confirmation";
  public static final String USER_JOINED_GROUP = "user.joined.group";
  public static final String MESSAGE_NOT_DELIVERED = "message.not.delivered";
  public static final String USER_MUST_REENGAGE = "user.must.reengage";
  public static final String USER_BLACKLISTED = "user.blacklisted";
  public static final String USER_NOT_IN_STREAM = "user.not.in.stream";
  public static final String USER_HAS_NOT_JOINED = "user.has.not.joined";
  public static final String MIM_NOT_ALLOWED = "mim.not.allowed";
  public static final String ROOM_NOT_ALLOWED = "room.not.allowed";
  public static final String ONBOARDING_SUCCESS = "onboarding.success";
  public static final String USER_INVITE_EXPIRED = "user.invite.expired";

  public void sendRawMessage(UserSession session, String streamId, String messageContent) {
    try {
      LOG.debug("Send message to symphony: {} - {}", streamId, messageContent);
      streamService.sendMessage(podConfiguration.getUrl(), streamId, messageContent, session).orElseThrow(SymphonySendMessageFailedProblem::new);
    } catch (Exception e) {
      LOG.error("Cannot send message on stream {}", streamId, e);
    }
  }

  public void sendRawMessage(String streamId, String fromSymphonyUserId, String messageContent) {
    FederatedAccount federatedAccount = federatedAccountRepository.findBySymphonyId(fromSymphonyUserId).orElseThrow(() -> {
      LOG.error("fromSymphonyUser {} not found", fromSymphonyUserId);
      return new SendMessageFailedProblem();
    });

    UserSession userSession = authenticationService.authenticate(
      podConfiguration.getSessionAuth(),
      podConfiguration.getKeyAuth(),
      federatedAccount.getSymphonyUsername(),
      chatConfiguration.getSharedPrivateKey().getData());

    sendRawMessage(userSession, streamId, messageContent);
  }

  // TODO
  /*public void sendUntemplatedMessage(String whatsAppId, String streamId, String messageI18nKey, Object... args) {
    String content = RendererUtils.resolveI18n(messageSource, messageI18nKey, args, messageI18nKey);
    sendRawMessage(whatsAppId, streamId, content);
  }

  public void sendInfoMessage(String whatsAppId, String streamId, String messageI18nKey, Object... args) {
    String content = RendererUtils.getRenderedMessage(messageSource, new RendererMessage(SmsRenderer.SmsTypes.INFORMATION, messageI18nKey, args));
    sendRawMessage(whatsAppId, streamId, content);
  }

  public void sendAlertMessage(String whatsAppId, String streamId, String messageI18nKey, Object... args) {
    String content = RendererUtils.getRenderedMessage(messageSource, new RendererMessage(SmsRenderer.SmsTypes.ALERT, messageI18nKey, args));
    sendRawMessage(whatsAppId, streamId, content);
  }

  public void sendUntemplatedMessage(ISymClient client, String streamId, String messageI18nKey, Object... args) {
    String content = RendererUtils.resolveI18n(messageSource, messageI18nKey, args, messageI18nKey);
    sendRawMessage(client, streamId, content);
  }

  public void sendInfoMessage(ISymClient client, String streamId, String messageI18nKey, Object... args) {
    String content = RendererUtils.getRenderedMessage(messageSource, new RendererMessage(SmsRenderer.SmsTypes.INFORMATION, messageI18nKey, args));
    sendRawMessage(client, streamId, content);
  }

  public void sendAlertMessage(ISymClient client, String streamId, String messageI18nKey, Object... args) {
    String content = RendererUtils.getRenderedMessage(messageSource, new RendererMessage(SmsRenderer.SmsTypes.ALERT, messageI18nKey, args));
    sendRawMessage(client, streamId, content);
  }*/

}
