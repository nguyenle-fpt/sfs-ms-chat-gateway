package com.symphony.sfs.ms.chat.service;

import com.amazonaws.util.Base64;
import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.datafeed.MessageDecryptor;
import com.symphony.sfs.ms.chat.datafeed.SBEEventMessage;
import com.symphony.sfs.ms.chat.exception.DecryptionException;
import com.symphony.sfs.ms.chat.exception.InlineReplyMessageException;
import com.symphony.sfs.ms.chat.generated.model.SendMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.SymphonyAttachment;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.sbe.MessageEncryptor;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyService;
import com.symphony.sfs.ms.chat.util.SymphonySystemMessageTemplateProcessor;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.security.StaticSessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonyAuthFactory;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyInboundMessage;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyOutboundAttachment;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyOutboundMessage;
import com.symphony.sfs.ms.starter.util.StreamUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseToSymphony.ENCRYPTION_FAILED;
import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseToSymphony.UNKNOWN_SENDER;

@Service
@Slf4j
@RequiredArgsConstructor
public class SymphonyMessageSender {

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
  public static final String SYSTEM_MESSAGE_ALERT_HANDLEBARS_TEMPLATE = "system_message_alert";
  public static final String SYSTEM_MESSAGE_INFORMATION_HANDLEBARS_TEMPLATE = "system_message_information";
  public static final String SYSTEM_MESSAGE_NOTIFICATION_HANDLEBARS_TEMPLATE = "system_message_notification";
  public static final String SYSTEM_MESSAGE_SIMPLE_HANDLEBARS_TEMPLATE = "system_message_simple";

  private final PodConfiguration podConfiguration;
  private final ChatConfiguration chatConfiguration;
  private final AuthenticationService authenticationService;
  private final FederatedAccountRepository federatedAccountRepository;
  private final StreamService streamService;
  private final SymphonySystemMessageTemplateProcessor templateProcessor;
  private final MessageIOMonitor messageMetrics;
  private final MessageEncryptor messageEncryptor;
  private final MessageDecryptor messageDecryptor;
  private final SymphonyService symphonyService;
  private final SymphonyAuthFactory symphonyAuthFactory;

  public Optional<String> sendRawMessage(SymphonySession session, String streamId, String messageContent) {
    LOG.debug("Send message to symphony");
    return streamService.sendMessage(podConfiguration.getUrl(), new StaticSessionSupplier<>(session), streamId, messageContent);
  }

  public Optional<String> sendRawMessage(String streamId, String fromSymphonyUserId, String messageContent, String toSymphonyUserId) {
    FederatedAccount federatedAccount = federatedAccountRepository.findBySymphonyId(fromSymphonyUserId).orElseThrow(() -> {
      LOG.error("fromSymphonyUser {} not found", fromSymphonyUserId);
      messageMetrics.onMessageBlockToSymphony(UNKNOWN_SENDER, streamId);
      return new SendMessageFailedProblem();
    });

    messageMetrics.onSendMessageToSymphony(fromSymphonyUserId, streamId);

    SymphonySession userSession = authenticationService.authenticate(
      podConfiguration.getSessionAuth(),
      podConfiguration.getKeyAuth(),
      federatedAccount.getSymphonyUsername(),
      chatConfiguration.getSharedPrivateKey().getData());

    return sendRawMessage(userSession, streamId, messageContent);
  }

  public Optional<String> sendSimpleMessage(String streamId, String fromSymphonyUserId, String messageContent, String toSymphonyUserId) {
    String detemplatized = templateProcessor.process(messageContent, SYSTEM_MESSAGE_SIMPLE_HANDLEBARS_TEMPLATE);
    return sendRawMessage(streamId, fromSymphonyUserId, detemplatized, toSymphonyUserId);
  }

  public Optional<String> sendSimpleMessage(SymphonySession userSession, String streamId, String messageContent) {
    String detemplatized = templateProcessor.process(messageContent, SYSTEM_MESSAGE_SIMPLE_HANDLEBARS_TEMPLATE);
    return sendRawMessage(userSession, streamId, detemplatized);
  }

  public Optional<String> sendNotificationMessage(String streamId, String fromSymphonyUserId, String messageContent, String toSymphonyUserId) {
    return sendRawMessage(streamId, fromSymphonyUserId, templateProcessor.process(messageContent, SYSTEM_MESSAGE_NOTIFICATION_HANDLEBARS_TEMPLATE), toSymphonyUserId);
  }

  public Optional<String> sendNotificationMessage(SymphonySession userSession, String streamId, String messageContent) {
    return sendRawMessage(userSession, streamId, templateProcessor.process(messageContent, SYSTEM_MESSAGE_NOTIFICATION_HANDLEBARS_TEMPLATE));
  }

  public Optional<String> sendInfoMessage(String streamId, String fromSymphonyUserId, String messageContent, String toSymphonyUserId) {
    return sendRawMessage(streamId, fromSymphonyUserId, templateProcessor.process(messageContent, SYSTEM_MESSAGE_INFORMATION_HANDLEBARS_TEMPLATE), toSymphonyUserId);
  }

  public Optional<String> sendInfoMessage(SymphonySession userSession, String streamId, String messageContent) {
    return sendRawMessage(userSession, streamId, templateProcessor.process(messageContent, SYSTEM_MESSAGE_INFORMATION_HANDLEBARS_TEMPLATE));
  }

  public Optional<String> sendAlertMessage(SymphonySession userSession, String streamId, String messageContent, String title, List<String> errors) {
    return sendRawMessage(userSession, streamId, templateProcessor.process(messageContent, title, errors, SYSTEM_MESSAGE_ALERT_HANDLEBARS_TEMPLATE));
  }

  public Optional<String> sendAlertMessage(SymphonySession userSession, String streamId, String messageContent, List<String> errors) {
    return sendAlertMessage(userSession, streamId, messageContent, null, errors);
  }

  public Optional<String> sendAlertMessage(String streamId, String fromSymphonyUserId, String messageContent, String toSymphonyUserId) {
    return sendRawMessage(streamId, fromSymphonyUserId, templateProcessor.process(messageContent, SYSTEM_MESSAGE_ALERT_HANDLEBARS_TEMPLATE), toSymphonyUserId);
  }

  public Optional<String> sendRawMessageWithAttachments(String streamId, String fromSymphonyUserId, String messageContent, String toSymphonyUserId, List<SymphonyAttachment> attachments) {
    FederatedAccount federatedAccount = federatedAccountRepository.findBySymphonyId(fromSymphonyUserId).orElseThrow(() -> {
      LOG.error("fromSymphonyUser {} not found", fromSymphonyUserId);
      messageMetrics.onMessageBlockToSymphony(UNKNOWN_SENDER, streamId);
      return new SendMessageFailedProblem();
    });

    messageMetrics.onSendMessageToSymphony(fromSymphonyUserId, streamId);

    SymphonySession userSession = authenticationService.authenticate(
      podConfiguration.getSessionAuth(),
      podConfiguration.getKeyAuth(),
      federatedAccount.getSymphonyUsername(),
      chatConfiguration.getSharedPrivateKey().getData());
    SymphonyOutboundMessage symphonyOutboundMessage = SymphonyOutboundMessage.builder()
      .message(messageContent)
      .attachment(attachments.stream().map(attachment ->
        SymphonyOutboundAttachment.builder()
          .name(attachment.getFileName())
          .data(Base64.decode(attachment.getData()))
          .mediaType(MediaType.parseMediaType(attachment.getContentType()))
          .build()
      ).toArray(SymphonyOutboundAttachment[]::new)).build();
    Optional<SymphonyInboundMessage> response =  streamService.sendMessageMultiPart(podConfiguration.getUrl(), new StaticSessionSupplier<>(userSession), streamId, symphonyOutboundMessage, false);
    return response.map(SymphonyInboundMessage::getMessageId);
  }

  public Optional<String> sendReplyMessage(String streamId, String fromSymphonyUserId, String messageContent, String parentMessageId) {
    FederatedAccount federatedAccount = federatedAccountRepository.findBySymphonyId(fromSymphonyUserId).orElseThrow(() -> {
      LOG.error("fromSymphonyUser {} not found", fromSymphonyUserId);
      messageMetrics.onMessageBlockToSymphony(UNKNOWN_SENDER, streamId);
      return new SendMessageFailedProblem();
    });

    messageMetrics.onSendMessageToSymphony(fromSymphonyUserId, streamId);

    SymphonySession userSession = authenticationService.authenticate(
      podConfiguration.getSessionAuth(),
      podConfiguration.getKeyAuth(),
      federatedAccount.getSymphonyUsername(),
      chatConfiguration.getSharedPrivateKey().getData());

    try {
      SBEEventMessage replyToMessage = getReplyMessage(fromSymphonyUserId, userSession, parentMessageId);
      if (replyToMessage.getAttachments() != null && replyToMessage.getAttachments().size() > 0) {
        throw new InlineReplyMessageException();
      }
      SBEEventMessage sbeMessageToBeSent = messageEncryptor.encrypt(fromSymphonyUserId, streamId, messageContent, replyToMessage);
      SBEEventMessage sentMessage = symphonyService.sendReplyMessage(sbeMessageToBeSent, userSession);
      return Optional.ofNullable(StreamUtil.toUrlSafeStreamId(sentMessage.getMessageId()));
    } catch (IOException e) {
      LOG.error("Unable to send relied message from WhatsApp: stream={} initiator={}", streamId, fromSymphonyUserId, e);
      messageMetrics.onMessageBlockToSymphony(ENCRYPTION_FAILED, streamId);
    }
    return Optional.empty();
  }

  private SBEEventMessage getReplyMessage(String userId, SymphonySession session, String messageId) throws DecryptionException {
    SBEEventMessage eventMessage;
    try {
      eventMessage = symphonyService.getEncryptedMessage(messageId, session).get();
    } catch (Exception e) {
      // See: https://perzoinc.atlassian.net/browse/CES-4690
      // Use gateway bot to retrieve message if the "messageId" belongs to a room and the bot is in the room
      // This is required when an whatsapp user was in a room, then getting removed, and added again
      // In this  case, the whatsapp service account could not retrieve historic message, only the whatsapp room bot could
      SymphonySession botSession = symphonyAuthFactory.getBotAuth().get();
      eventMessage = symphonyService.getEncryptedMessage(messageId, botSession).get();
    }
    messageDecryptor.decrypt(eventMessage, userId);

    return eventMessage;
  }

    // TODO i18n?
//  public void sendUntemplatedMessage(String whatsAppId, String streamId, String messageI18nKey, Object... args) {
//    String content = RendererUtils.resolveI18n(messageSource, messageI18nKey, args, messageI18nKey);
//    sendRawMessage(whatsAppId, streamId, content);
//  }

//  public void sendAlertMessage(String whatsAppId, String streamId, String messageI18nKey, Object... args) {
//    String content = RendererUtils.getRenderedMessage(messageSource, new RendererMessage(SmsRenderer.SmsTypes.ALERT, messageI18nKey, args));
//    sendRawMessage(whatsAppId, streamId, content);
//  }
//
//  public void sendUntemplatedMessage(ISymClient client, String streamId, String messageI18nKey, Object... args) {
//    String content = RendererUtils.resolveI18n(messageSource, messageI18nKey, args, messageI18nKey);
//    sendRawMessage(client, streamId, content);
//  }
//
//  public void sendInfoMessage(ISymClient client, String streamId, String messageI18nKey, Object... args) {
//    String content = RendererUtils.getRenderedMessage(messageSource, new RendererMessage(SmsRenderer.SmsTypes.INFORMATION, messageI18nKey, args));
//    sendRawMessage(client, streamId, content);
//  }
//
//  public void sendAlertMessage(ISymClient client, String streamId, String messageI18nKey, Object... args) {
//    String content = RendererUtils.getRenderedMessage(messageSource, new RendererMessage(SmsRenderer.SmsTypes.ALERT, messageI18nKey, args));
//    sendRawMessage(client, streamId, content);
//  }

}
