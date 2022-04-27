package com.symphony.sfs.ms.chat.sbe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.symphony.oss.models.core.canon.facade.ThreadId;
import com.symphony.security.exceptions.CiphertextTransportVersionException;
import com.symphony.security.exceptions.SymphonyEncryptionException;
import com.symphony.security.exceptions.SymphonyInputException;
import com.symphony.security.helper.ClientCryptoHandler;
import com.symphony.security.helper.IClientCryptoHandler;
import com.symphony.security.helper.KeyIdentifier;
import com.symphony.sfs.ms.chat.datafeed.MessageEntityData;
import com.symphony.sfs.ms.chat.exception.EncryptionException;
import com.symphony.sfs.ms.starter.emojis.EmojiService;
import com.symphony.sfs.ms.starter.symphony.crypto.ContentKeyManager;
import com.symphony.sfs.ms.starter.symphony.crypto.exception.UnknownUserException;
import com.symphony.sfs.ms.starter.symphony.stream.CustomEntity;
import com.symphony.sfs.ms.starter.symphony.stream.EventUser;
import com.symphony.sfs.ms.starter.symphony.stream.MessageAttachment;
import com.symphony.sfs.ms.starter.symphony.stream.SBEEventMessage;
import com.symphony.sfs.ms.starter.util.StreamUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Base64;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

@Component
@Log4j2
public class MessageEncryptor {
  private IClientCryptoHandler cryptoHandler;
  private final ContentKeyManager contentKeyManager;
  private final EmojiService emojiService = new EmojiService();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private static final String FORWARDED_HEADER = "\n\n**Forwarded Message:**\n";

  public MessageEncryptor(ContentKeyManager contentKeyManager) {
    this.contentKeyManager = contentKeyManager;
    this.cryptoHandler = new ClientCryptoHandler();
  }


  public SBEEventMessage buildReplyMessage(String userId, String userName, String streamId, String messageText, SBEEventMessage repliedToMessage, List<MessageAttachment> attachments) throws EncryptionException {
    try {
      String threadId = StreamUtil.fromUrlSafeStreamId(streamId);
      KeyIdentifier keyId = contentKeyManager.getContentKeyIdentifier(threadId, userId, userName);
      byte[] contentKey = contentKeyManager.getContentKey(ThreadId.newBuilder().build(threadId), userId, userName, keyId.getRotationId());

      String text = emojiService.convertEmojisFromUtf8ToMarkdown(repliedToMessage == null ? messageText : generateRepliedMessageText(messageText, repliedToMessage));

      String presentationML = repliedToMessage == null ? String.format("<div data-format=\"PresentationML\" data-version=\"2.0\" class=\"wysiwyg\"><p>%s</p></div>", emojiService.convertEmojisFromUtf8ToMessageML(messageText)) : null;

      String customEntitiesText = repliedToMessage == null ? null : generateReplyCustomEntities(repliedToMessage, attachments);

      return generateSBEEventMessage(keyId, contentKey, userId, threadId, text, presentationML, customEntitiesText, repliedToMessage, attachments);

    } catch (IOException | UnknownUserException e) {
      throw new EncryptionException(e);
    }
  }

  public SBEEventMessage buildForwardedMessage(String userId, String userName, String streamId, String messageText, String forwardedPrefix, List<MessageAttachment> blastAttachments, byte[] ephemeralKey) throws EncryptionException {
    try {
      String threadId = StreamUtil.fromUrlSafeStreamId(streamId);
      KeyIdentifier keyId = contentKeyManager.getContentKeyIdentifier(threadId, userId, userName);
      byte[] contentKey = contentKeyManager.getContentKey(ThreadId.newBuilder().build(threadId), userId, userName, keyId.getRotationId());
      String convertedMessageText = emojiService.convertEmojisFromUtf8ToMarkdown(messageText);
      String text = FORWARDED_HEADER + forwardedPrefix + convertedMessageText;

      String customEntitiesText = generateForwardedCustomEntities(forwardedPrefix, convertedMessageText);


      SBEEventMessage sbeEventMessage = generateSBEEventMessage(keyId, contentKey, userId, threadId, text, null, customEntitiesText, null, Collections.emptyList());
      sbeEventMessage.setFormat("com.symphony.markdown");
      sbeEventMessage.setChatType("CHATROOM");
      sbeEventMessage.setMsgFeatures(7);

      if (blastAttachments != null && !blastAttachments.isEmpty()) {
        String encryptedFileKey = encrypt(contentKey, keyId, Base64.encodeBase64String(ephemeralKey));
        sbeEventMessage.setEncryptedFileKey(encryptedFileKey);
        sbeEventMessage.setFileKeyEncryptedAttachments(blastAttachments);
      }

      return sbeEventMessage;

    } catch (IOException | UnknownUserException e) {
      throw new EncryptionException(e);
    }
  }




  public SBEEventMessage generateSBEEventMessage(KeyIdentifier keyId, byte[] contentKey,
                                                 String userId, String threadId, String text,
                                                 String presentationML, String customEntitiesText,
                                                 SBEEventMessage repliedToMessage,
                                                 List<MessageAttachment> attachments) throws JsonProcessingException, EncryptionException {
    return SBEEventMessage.builder()
      .threadId(threadId)
      .parentMessageId(repliedToMessage != null ? repliedToMessage.getMessageId() : null)
      .parentRelationshipType(repliedToMessage != null ? "REPLY" : null)
      .text(encrypt(contentKey, keyId, text))
      .presentationML(presentationML == null ? null : encrypt(contentKey, keyId, presentationML))
      .encryptedMedia(encrypt(contentKey, keyId, "{\"content\":[],\"mediaType\":\"JSON\"}"))
      .encryptedEntities(encrypt(contentKey, keyId, "{}"))
      .entityJSON(encrypt(contentKey, keyId, "{}"))
      .customEntities(customEntitiesText == null ? null : encrypt(contentKey, keyId, customEntitiesText))
      .entities(objectMapper.readValue("{}", Object.class))
      .attachments(attachments)
      .msgFeatures(repliedToMessage == null ? 7 : 3)
      .version(SBEEventMessage.Versions.SOCIALMESSAGE.toString())
      .format(repliedToMessage == null ? "com.symphony.messageml.v2" : "com.symphony.markdown")
      .from(EventUser.builder().id(Long.parseLong(userId)).build())
      .chatType(repliedToMessage == null ? "INSTANT_CHAT" : "CHATROOM")
      .build();
  }

  @VisibleForTesting
  public String encrypt(byte[] contentKey, KeyIdentifier keyId, String content) throws EncryptionException {
    byte[] encryptedBytes = encrypt(contentKey, keyId, content.getBytes(StandardCharsets.UTF_8));
    return Base64.encodeBase64String(encryptedBytes);
  }

  private byte[] encrypt(byte[] contentKey, KeyIdentifier keyId, byte[] input) throws EncryptionException {
    try {
      return cryptoHandler.encryptMsg(contentKey, keyId, input);
    } catch (SymphonyInputException | CiphertextTransportVersionException | SymphonyEncryptionException e) {
      LOG.error("Exception encrypting message", e);
      throw new EncryptionException(e);
    }
  }


  private String generateReplyCustomEntities(SBEEventMessage repliedToMessage, List<MessageAttachment> attachments) throws IOException {
    String prefix = generatePrefix(repliedToMessage);
    CustomEntity customEntity = new CustomEntity();
    customEntity.setType(CustomEntity.QUOTE_TYPE);
    customEntity.setBeginIndex(0);
    customEntity.setEndIndex(prefix.length());
    customEntity.setVersion("0.0.1");
    MessageEntityData customEntityData = getQuotingEntityData(repliedToMessage, attachments);
    customEntity.setData(objectMapper.convertValue(customEntityData, Map.class));

    return objectMapper.writeValueAsString(Collections.singletonList(customEntity));
  }
  private String generateForwardedCustomEntities(String forwardedPrefix, String text) throws IOException {
    CustomEntity customEntity = new CustomEntity();
    customEntity.setType(CustomEntity.FORWARDED_TYPE);
    customEntity.setBeginIndex(0);
    customEntity.setEndIndex(text.length() + forwardedPrefix.length() + FORWARDED_HEADER.length());
    customEntity.setVersion("0.0.1");

    MessageEntityData customEntityData = buildForwardedMessageEntityData(forwardedPrefix, text);
    customEntity.setData(objectMapper.convertValue(customEntityData, Map.class));

    return objectMapper.writeValueAsString(Collections.singletonList(customEntity));
  }

  private MessageEntityData buildForwardedMessageEntityData(String forwardedPrefix, String text) {
    return MessageEntityData.builder()
      .text(text)
      .metadata(forwardedPrefix)
      .build();
  }

  private MessageEntityData getQuotingEntityData(SBEEventMessage repliedToMessage, List<MessageAttachment> attachments) throws IOException {
    return MessageEntityData.builder()
      .id(repliedToMessage.getMessageId())
      .streamId(repliedToMessage.getStreamId())
      .presentationML(repliedToMessage.getPresentationML())
      .text(getPureText(repliedToMessage))
      .ingestionDate(repliedToMessage.getIngestionDate())
      .metadata(generateRepliedMessageMetaData(repliedToMessage))
      .attachments(attachments)
      .entities(objectMapper.readTree(Optional.ofNullable(repliedToMessage.getEncryptedEntities()).orElse("{ \"hashtags\": [], \"userMentions\": [], \"urls\": [] }")))
      .customEntities(Collections.emptyList())
      .entityJSON(objectMapper.readTree(Optional.ofNullable(repliedToMessage.getEntityJSON()).orElse("{}")))
      .build();
  }

  private String generateRepliedMessageMetaData(SBEEventMessage repliedToMessage) {
    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy @ hh:mm");
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    String dateTime = sdf.format(new Date(repliedToMessage.getIngestionDate()));
    return String.format("%s %s",
        repliedToMessage.getFrom().getPrettyName(),
        dateTime);
  }

  private String generateRepliedMessageText(String messageText, SBEEventMessage repliedToMessage) throws IOException {
    return generatePrefix(repliedToMessage) + messageText;
  }

  private String getPureText(SBEEventMessage repliedToMessage) throws IOException {
    String text = repliedToMessage.getText();
    if (!org.springframework.util.StringUtils.isEmpty(repliedToMessage.getCustomEntities())) {
      List<CustomEntity> customEntities = objectMapper.readValue(repliedToMessage.getCustomEntities(), objectMapper.getTypeFactory().constructCollectionType(List.class, CustomEntity.class));
      Optional<CustomEntity> replyEntity = customEntities.stream().filter(c -> c.getType().equals(CustomEntity.QUOTE_TYPE)).findFirst();
      Optional<CustomEntity> forwardEntity = customEntities.stream().filter(c -> c.getType().equals(CustomEntity.FORWARDED_TYPE)).findFirst();
      if (replyEntity.isPresent()) {
        return text.substring(replyEntity.get().getEndIndex());
      } else if (forwardEntity.isPresent() && forwardEntity.get().getBeginIndex() > 0) {
        return text.substring(0, forwardEntity.get().getBeginIndex() - 1);
      }
    }
    return text;
  }

  private String generatePrefix(SBEEventMessage repliedToMessage) throws IOException {
    String text = getPureText(repliedToMessage);
    String dash = org.apache.commons.lang3.StringUtils.repeat('\u2014', 11);
    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy @ hh:mm");
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    String dateTime = sdf.format(new Date(repliedToMessage.getIngestionDate()));
    return String.format("**In reply to:**%n**%s %s**%n_%s_%n%s%n",
        repliedToMessage.getFrom().getPrettyName(),
        dateTime,
        text,
        dash);
  }
}
