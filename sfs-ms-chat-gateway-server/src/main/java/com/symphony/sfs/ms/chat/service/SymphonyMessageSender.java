package com.symphony.sfs.ms.chat.service;

import com.amazonaws.util.Base64;
import com.symphony.security.helper.ClientCryptoHandler;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.MessageDecryptor;
import com.symphony.sfs.ms.chat.exception.DecryptionException;
import com.symphony.sfs.ms.chat.exception.BlastAttachmentUploadException;
import com.symphony.sfs.ms.chat.exception.InlineReplyMessageException;
import com.symphony.sfs.ms.chat.generated.model.AttachmentInfo;
import com.symphony.sfs.ms.chat.generated.model.MessageInfo;
import com.symphony.sfs.ms.chat.generated.model.MessageInfoWithCustomEntities;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.SendMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.SymphonyAttachment;
import com.symphony.sfs.ms.chat.mapper.MessageInfoMapper;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.sbe.MessageEncryptor;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyService;
import com.symphony.sfs.ms.chat.util.SymphonySystemMessageTemplateProcessor;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.security.SessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.CustomEntity;
import com.symphony.sfs.ms.starter.symphony.stream.MessageAttachment;
import com.symphony.sfs.ms.starter.symphony.stream.SBEEventMessage;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyInboundMessage;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyOutboundAttachment;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyOutboundMessage;
import com.symphony.sfs.ms.starter.util.StreamUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseToSymphony.BLAST_ATTACHMENTS_UPLOAD_FAILED;
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
  private final DatafeedSessionPool datafeedSessionPool;
  private final FederatedAccountRepository federatedAccountRepository;
  private final StreamService streamService;
  private final SymphonySystemMessageTemplateProcessor templateProcessor;
  private final MessageIOMonitor messageMetrics;
  private final MessageEncryptor messageEncryptor;
  private final MessageDecryptor messageDecryptor;
  private final SymphonyService symphonyService;
  private final EmpSchemaService empSchemaService;
  private final MessageSource messageSource;
  private final MessageInfoMapper messageInfoMapper;


  public Optional<MessageInfoWithCustomEntities> sendRawMessage(SessionSupplier<SymphonySession> session, String streamId, String messageContent) {
    LOG.debug("Send message to symphony");
    return Optional.of(messageInfoMapper.inboundMessageToMessageInfo(streamService.sendMessage(podConfiguration.getUrl(), session, streamId, messageContent).orElseThrow(SendMessageFailedProblem::new)));
  }

  public Optional<MessageInfoWithCustomEntities> sendRawMessage(String streamId, String fromSymphonyUserId, String messageContent, String toSymphonyUserId) {
    FederatedAccount federatedAccount = federatedAccountRepository.findBySymphonyId(fromSymphonyUserId).orElseThrow(() -> {
      LOG.error("fromSymphonyUser {} not found", fromSymphonyUserId);
      messageMetrics.onMessageBlockToSymphony(UNKNOWN_SENDER, streamId);
      return new SendMessageFailedProblem();
    });

    messageMetrics.onSendMessageToSymphony(fromSymphonyUserId, streamId);

    return sendRawMessage(datafeedSessionPool.getSessionSupplier(federatedAccount), streamId, messageContent);
  }

  public Optional<MessageInfoWithCustomEntities> sendSimpleMessage(String streamId, String fromSymphonyUserId, String messageContent, String toSymphonyUserId) {
    String detemplatized = templateProcessor.process(messageContent, SYSTEM_MESSAGE_SIMPLE_HANDLEBARS_TEMPLATE);
    return sendRawMessage(streamId, fromSymphonyUserId, detemplatized, toSymphonyUserId);
  }

  public Optional<MessageInfoWithCustomEntities> sendSimpleMessage(SessionSupplier<SymphonySession> userSession, String streamId, String messageContent) {
    String detemplatized = templateProcessor.process(messageContent, SYSTEM_MESSAGE_SIMPLE_HANDLEBARS_TEMPLATE);
    return sendRawMessage(userSession, streamId, detemplatized);
  }

  public Optional<MessageInfoWithCustomEntities> sendNotificationMessage(String streamId, String fromSymphonyUserId, String messageContent, String toSymphonyUserId) {
    return sendRawMessage(streamId, fromSymphonyUserId, templateProcessor.process(messageContent, SYSTEM_MESSAGE_NOTIFICATION_HANDLEBARS_TEMPLATE), toSymphonyUserId);
  }

  public Optional<MessageInfoWithCustomEntities> sendNotificationMessage(SessionSupplier<SymphonySession> userSession, String streamId, String messageContent) {
    return sendRawMessage(userSession, streamId, templateProcessor.process(messageContent, SYSTEM_MESSAGE_NOTIFICATION_HANDLEBARS_TEMPLATE));
  }

  public Optional<MessageInfoWithCustomEntities> sendInfoMessage(String streamId, String fromSymphonyUserId, String messageContent, String toSymphonyUserId) {
    return sendRawMessage(streamId, fromSymphonyUserId, templateProcessor.process(messageContent, SYSTEM_MESSAGE_INFORMATION_HANDLEBARS_TEMPLATE), toSymphonyUserId);
  }

  public Optional<MessageInfoWithCustomEntities> sendInfoMessage(SessionSupplier<SymphonySession> userSession, String streamId, String messageContent) {
    return sendRawMessage(userSession, streamId, templateProcessor.process(messageContent, SYSTEM_MESSAGE_INFORMATION_HANDLEBARS_TEMPLATE));
  }

  public Optional<MessageInfoWithCustomEntities> sendAlertMessage(SessionSupplier<SymphonySession> userSession, String streamId, String messageContent, String title, List<String> errors) {
    return sendRawMessage(userSession, streamId, templateProcessor.process(messageContent, title, errors, SYSTEM_MESSAGE_ALERT_HANDLEBARS_TEMPLATE));
  }

  public Optional<MessageInfoWithCustomEntities> sendAlertMessage(SessionSupplier<SymphonySession> userSession, String streamId, String messageContent, List<String> errors) {
    return sendAlertMessage(userSession, streamId, messageContent, null, errors);
  }

  public Optional<MessageInfoWithCustomEntities> sendAlertMessage(String streamId, String fromSymphonyUserId, String messageContent, String toSymphonyUserId) {
    return sendRawMessage(streamId, fromSymphonyUserId, templateProcessor.process(messageContent, SYSTEM_MESSAGE_ALERT_HANDLEBARS_TEMPLATE), toSymphonyUserId);
  }

  public Optional<MessageInfoWithCustomEntities> sendRawMessageWithAttachments(String streamId, String fromSymphonyUserId, String messageContent, String toSymphonyUserId, List<SymphonyAttachment> attachments) {
    FederatedAccount federatedAccount = federatedAccountRepository.findBySymphonyId(fromSymphonyUserId).orElseThrow(() -> {
      LOG.error("fromSymphonyUser {} not found", fromSymphonyUserId);
      messageMetrics.onMessageBlockToSymphony(UNKNOWN_SENDER, streamId);
      return new SendMessageFailedProblem();
    });

    messageMetrics.onSendMessageToSymphony(fromSymphonyUserId, streamId);

    SymphonyOutboundMessage symphonyOutboundMessage = SymphonyOutboundMessage.builder()
      .message(messageContent)
      .attachment(attachments.stream().map(attachment ->
        SymphonyOutboundAttachment.builder()
          .name(attachment.getFileName())
          .data(Base64.decode(attachment.getData()))
          .mediaType(MediaType.parseMediaType(attachment.getContentType()))
          .build()
      ).toArray(SymphonyOutboundAttachment[]::new)).build();
    Optional<SymphonyInboundMessage> response =  streamService.sendMessageMultiPart(podConfiguration.getUrl(), datafeedSessionPool.getSessionSupplier(federatedAccount), streamId, symphonyOutboundMessage, false);
    return Optional.of(messageInfoMapper.symphonyInboundMessageToMessageInfo(response.orElseThrow(SendMessageFailedProblem::new)));
  }

  public Optional<MessageInfoWithCustomEntities> sendForwardedMessage(String streamId, String fromSymphonyUserId, String messageContent, List<SymphonyAttachment> attachments) {
    FederatedAccount federatedAccount = federatedAccountRepository.findBySymphonyId(fromSymphonyUserId).orElseThrow(() -> {
      LOG.error("fromSymphonyUser {} not found", fromSymphonyUserId);
      messageMetrics.onMessageBlockToSymphony(UNKNOWN_SENDER, streamId);
      return new SendMessageFailedProblem();
    });

    messageMetrics.onSendMessageToSymphony(fromSymphonyUserId, streamId);

    SessionSupplier<SymphonySession> userSession = datafeedSessionPool.getSessionSupplier(federatedAccount);

    try {
      byte[] ephemeralKey = this.generateEphemeralKey();
      List<MessageAttachment> blastAttachments = new ArrayList<>();
      if (attachments != null && !attachments.isEmpty()) {
        for (SymphonyAttachment attachment : attachments) {
          MessageAttachment blastAttachment = uploadBlastAttachment(userSession, attachment, ephemeralKey).orElseThrow(BlastAttachmentUploadException::new);
          blastAttachments.add(blastAttachment);
        }
      }

      String messageHeader = messageSource.getMessage("forwarded.message.header", new Object[] {empSchemaService.getEmpDisplayName(federatedAccount.getEmp())}, Locale.getDefault());
      SBEEventMessage sbeMessageToBeSent = messageEncryptor.buildForwardedMessage(fromSymphonyUserId, federatedAccount.getSymphonyUsername(), streamId, messageContent, messageHeader, blastAttachments, ephemeralKey);
      SBEEventMessage sentMessage = symphonyService.sendBulkMessage(sbeMessageToBeSent, userSession);

      return Optional.of(decryptAndBuildMessageInfo(sentMessage, fromSymphonyUserId, userSession));
    } catch (BlastAttachmentUploadException e) {
      LOG.error("Unable to forward attachment to Symphony: stream={} initiator={}", streamId, fromSymphonyUserId, e);
      messageMetrics.onMessageBlockToSymphony(BLAST_ATTACHMENTS_UPLOAD_FAILED, streamId);
    } catch (IOException e) {
      LOG.error("Unable to forward message to Symphony: stream={} initiator={}", streamId, fromSymphonyUserId, e);
      messageMetrics.onMessageBlockToSymphony(ENCRYPTION_FAILED, streamId);
    }
    return Optional.empty();
  }


  public Optional<MessageInfoWithCustomEntities> sendReplyMessage(String streamId, String fromSymphonyUserId, String messageContent, String parentMessageId, boolean attachmentReplySupported, Optional<List<String>> attachmentMessageIds) {
    FederatedAccount federatedAccount = federatedAccountRepository.findBySymphonyId(fromSymphonyUserId).orElseThrow(() -> {
      LOG.error("fromSymphonyUser {} not found", fromSymphonyUserId);
      messageMetrics.onMessageBlockToSymphony(UNKNOWN_SENDER, streamId);
      return new SendMessageFailedProblem();
    });

    messageMetrics.onSendMessageToSymphony(fromSymphonyUserId, streamId);

    SessionSupplier<SymphonySession> userSession = datafeedSessionPool.getSessionSupplier(federatedAccount);

    try {
      SBEEventMessage replyToMessage = getReplyMessage(fromSymphonyUserId, userSession, parentMessageId);
      if (!attachmentReplySupported &&
          (replyToMessage.getAttachments() != null && replyToMessage.getAttachments().size() > 0
            || attachmentMessageIds.isPresent() && attachmentMessageIds.get().size() > 0)) {
        throw new InlineReplyMessageException();
      }


      List<MessageAttachment> attachmentsFromMessageIds = attachmentMessageIds.orElse(Collections.emptyList())
        .stream().flatMap(id -> {
        try {
          return getReplyMessage(fromSymphonyUserId, userSession, id).getAttachments().stream();
        } catch (DecryptionException e) {
          LOG.error("Unable to decrypt message {} from WhatsApp: stream={} initiator={}", id, streamId, fromSymphonyUserId, e);
          return Stream.empty();
        }
      }).collect(Collectors.toList());

      List<MessageAttachment> allAttachments = Stream.of(
        replyToMessage.getAttachments(),
        attachmentsFromMessageIds
      ).filter(Objects::nonNull)
        .flatMap(List::stream)
        .collect(Collectors.toList());

      SBEEventMessage sbeMessageToBeSent = messageEncryptor.buildReplyMessage(fromSymphonyUserId, federatedAccount.getSymphonyUsername(), streamId, messageContent, replyToMessage, allAttachments);
      SBEEventMessage sentMessage = symphonyService.sendReplyMessage(sbeMessageToBeSent, userSession);

      return Optional.of(decryptAndBuildMessageInfo(sentMessage, fromSymphonyUserId, userSession));
    } catch (IOException e) {
      LOG.error("Unable to send relied message from WhatsApp: stream={} initiator={}", streamId, fromSymphonyUserId, e);
      messageMetrics.onMessageBlockToSymphony(ENCRYPTION_FAILED, streamId);
    }
    return Optional.empty();
  }

  private Optional<MessageAttachment> uploadBlastAttachment(SessionSupplier<SymphonySession> session, SymphonyAttachment attachment, byte[] ephemeralKey) {
    try {
      byte[] encryptedBytes = new ClientCryptoHandler().encryptMsgWithRotationIdZero(ephemeralKey, Base64.decode(attachment.getData()));
      MessageAttachment[] attachments = symphonyService.uploadBlastAttachment(session, attachment.getContentType(), attachment.getFileName(), encryptedBytes);
      return attachments != null && attachments.length > 0 ? Optional.of(attachments[0]) : Optional.empty();
    } catch (Exception e) {
      throw new BlastAttachmentUploadException();
    }
  }

  private byte[] generateEphemeralKey() {
    byte[] key = new byte[32];
    // Pick some secure values.
    SecureRandom SR = new SecureRandom();
    SR.nextBytes(key);
    return key;
  }

  private SBEEventMessage getReplyMessage(String userId, SessionSupplier<SymphonySession> session, String messageId) throws DecryptionException {
    SBEEventMessage eventMessage;
    try {
      eventMessage = symphonyService.getEncryptedMessage(messageId, session).get();
    } catch (Exception e) {
      // See: https://perzoinc.atlassian.net/browse/CES-4690
      // Use gateway bot to retrieve message if the "messageId" belongs to a room and the bot is in the room
      // This is required when an Connect user was in a room, then getting removed, and added again
      // In this  case, the Connect service account could not retrieve historic message, only the Connect room bot could
      eventMessage = symphonyService.getEncryptedMessage(messageId, datafeedSessionPool.getBotSessionSupplier()).get();
    }
    messageDecryptor.decrypt(eventMessage, userId, session.getPrincipal());

    return eventMessage;
  }
  public MessageInfoWithCustomEntities decryptAndBuildMessageInfo(SBEEventMessage sbeEventMessage, String symphonyUserId, SessionSupplier<SymphonySession> userSession) throws DecryptionException {
      return decryptAndBuildMessageInfo(sbeEventMessage, symphonyUserId, userSession, new HashMap<>());
  }


    public MessageInfoWithCustomEntities decryptAndBuildMessageInfo(SBEEventMessage sbeEventMessage, String symphonyUserId, SessionSupplier<SymphonySession> userSession, Map<String, SBEEventMessage> retrievedMessages) throws DecryptionException {
    messageDecryptor.decrypt(sbeEventMessage, symphonyUserId, userSession.getPrincipal());

    // TODO handle inline replies

    MessageInfoWithCustomEntities messageInfo = buildMessageInfo(sbeEventMessage);

    Optional<CustomEntity> quote = sbeEventMessage.getCustomEntity(CustomEntity.QUOTE_TYPE);

    if (quote.isPresent()) {
      messageInfo.setMessage(messageInfo.getMessage().substring(quote.get().getEndIndex()));
      String quotedId = StreamUtil.toUrlSafeStreamId(quote.get().getData().get("id").toString());
      Optional<SBEEventMessage> inlineMessageOptional = Optional.empty();
      if (retrievedMessages.containsKey(quotedId)) {
        inlineMessageOptional = Optional.of(retrievedMessages.get(quotedId));
      }
      if(inlineMessageOptional.isEmpty()) {
        inlineMessageOptional = symphonyService.getEncryptedMessage(quotedId, userSession);
      }
      if (inlineMessageOptional.isEmpty()) {
        // The message might not been retrieve with the federated account session
        // We try with the connect bot session
        inlineMessageOptional = symphonyService.getEncryptedMessage(quotedId, datafeedSessionPool.getBotSessionSupplier());
      }
      if (inlineMessageOptional.isEmpty()) {
        throw new RetrieveMessageFailedProblem();
      }

      SBEEventMessage inlineMessage = inlineMessageOptional.get();

      messageDecryptor.decrypt(inlineMessage, symphonyUserId, userSession.getPrincipal());
      MessageInfo inlineMessageInfo = buildMessageInfo(inlineMessage);
      Optional<CustomEntity> quoteInline = inlineMessage.getCustomEntity(CustomEntity.QUOTE_TYPE);

      if (quoteInline.isPresent()) {
        inlineMessageInfo.setMessage(inlineMessageInfo.getMessage().substring(quoteInline.get().getEndIndex()));
      } else {
        inlineMessageInfo.setMessage(inlineMessageInfo.getMessage());
      }

      messageInfo.setParentMessage(inlineMessageInfo);
    } else {
      messageInfo.setMessage(messageInfo.getMessage());
    }

    return messageInfo;
  }

  private MessageInfoWithCustomEntities buildMessageInfo(SBEEventMessage sbeEventMessage) {
    MessageInfoWithCustomEntities messageInfo = messageInfoMapper.sbeEventMessageToMessageInfo(sbeEventMessage);
    messageInfo.setMessageId(StreamUtil.toUrlSafeStreamId(sbeEventMessage.getMessageId()));

    if(sbeEventMessage.getAttachments() != null) {
      List<AttachmentInfo> attachmentInfos = sbeEventMessage.getAttachments()
        .stream()
        .map(sbeMessageAttachment -> new AttachmentInfo().contentType(sbeMessageAttachment.getContentType()).fileName(sbeMessageAttachment.getName()))
        .collect(Collectors.toList());

      messageInfo.setAttachments(attachmentInfos);
    }

    return messageInfo;
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
