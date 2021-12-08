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
import com.symphony.sfs.ms.chat.datafeed.ContentKeyManager;
import com.symphony.sfs.ms.chat.datafeed.CustomEntity;
import com.symphony.sfs.ms.chat.datafeed.MessageEntityData;
import com.symphony.sfs.ms.chat.datafeed.SBEEventMessage;
import com.symphony.sfs.ms.chat.datafeed.SBEEventUser;
import com.symphony.sfs.ms.chat.exception.EncryptionException;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import com.symphony.sfs.ms.starter.emojis.EmojiService;
import com.symphony.sfs.ms.starter.util.StreamUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Base64;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
@Log4j2
public class MessageEncryptor {
  private IClientCryptoHandler cryptoHandler;
  private final ContentKeyManager contentKeyManager;
  private final EmojiService emojiService = new EmojiService();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public MessageEncryptor(ContentKeyManager contentKeyManager) {
    this.contentKeyManager = contentKeyManager;
    this.cryptoHandler = new ClientCryptoHandler();
  }

  public SBEEventMessage encrypt(String userId, String streamId, String messageText, SBEEventMessage repliedToMessage) throws EncryptionException {
    try {
      String threadId = StreamUtil.fromUrlSafeStreamId(streamId);
      KeyIdentifier keyId = contentKeyManager.getContentKeyIdentifier(threadId, userId);
      byte[] contentKey = contentKeyManager.getContentKey(ThreadId.newBuilder().build(threadId), userId, keyId.getRotationId());

      String text = emojiService.convertEmojisFromUtf8ToMarkdown(repliedToMessage == null ? messageText : generateRepliedMessageText(messageText, repliedToMessage));

      String presentationML = repliedToMessage == null ? String.format("<div data-format=\"PresentationML\" data-version=\"2.0\" class=\"wysiwyg\"><p>%s</p></div>", emojiService.convertEmojisFromUtf8ToMessageML(messageText)) : null;

      String customEntitiesText = repliedToMessage == null ? null : generateCustomEntitiesText(repliedToMessage);

      return generateSBEEventMessage(keyId, contentKey, userId, threadId, text, presentationML, customEntitiesText, repliedToMessage);

    } catch (UnknownDatafeedUserException | IOException e) {
      throw new EncryptionException(e);
    }
  }


  public SBEEventMessage generateSBEEventMessage(KeyIdentifier keyId, byte[] contentKey,
                                                 String userId, String threadId, String text,
                                                 String presentationML, String customEntitiesText,
                                                 SBEEventMessage repliedToMessage) throws JsonProcessingException, EncryptionException {
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
      .attachments(Collections.emptyList())
      .msgFeatures(repliedToMessage == null ? 7 : 3)
      .version(SBEEventMessage.Versions.SOCIALMESSAGE.toString())
      .format(repliedToMessage == null ? "com.symphony.messageml.v2" : "com.symphony.markdown")
      .from(SBEEventUser.builder().id(Long.parseLong(userId)).build())
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

  private String generateCustomEntitiesText(SBEEventMessage repliedToMessage) throws IOException {
    String prefix = generatePrefix(repliedToMessage);
    CustomEntity customEntity = new CustomEntity();
    customEntity.setType(CustomEntity.QUOTE_TYPE);
    customEntity.setBeginIndex(0);
    customEntity.setEndIndex(prefix.length());
    customEntity.setVersion("0.0.1");
    MessageEntityData customEntityData = getQuotingEntityData(repliedToMessage);
    customEntity.setData(objectMapper.convertValue(customEntityData, Map.class));

    return objectMapper.writeValueAsString(Collections.singletonList(customEntity));
  }

  private MessageEntityData getQuotingEntityData(SBEEventMessage repliedToMessage) throws IOException {
    return MessageEntityData.builder()
      .id(repliedToMessage.getMessageId())
      .streamId(repliedToMessage.getStreamId())
      .presentationML(repliedToMessage.getPresentationML())
      .text(getPureText(repliedToMessage))
      .ingestionDate(repliedToMessage.getIngestionDate())
      .metadata(generateRepliedMessageMetaData(repliedToMessage))
      .attachments(Collections.emptyList())
      .entities(objectMapper.readTree(repliedToMessage.getEncryptedEntities()))
      .customEntities(Collections.emptyList())
      .entityJSON(objectMapper.readTree(repliedToMessage.getEntityJSON()))
      .build();
  }

  private String generateRepliedMessageMetaData(SBEEventMessage repliedToMessage) {
    String dateTime = new SimpleDateFormat("dd/MM/yy @ hh:mm").format(new Date(repliedToMessage.getIngestionDate()));
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
      if (replyEntity.isPresent()) {
        return text.substring(replyEntity.get().getEndIndex());
      }
    }
    return text;
  }

  private String generatePrefix(SBEEventMessage repliedToMessage) throws IOException {
    String text = getPureText(repliedToMessage);
    String dash = org.apache.commons.lang3.StringUtils.repeat('\u2014', 11);
    String dateTime = new SimpleDateFormat("dd/MM/yy @ hh:mm").format(new Date(repliedToMessage.getIngestionDate()));
    return String.format("**In reply to:**%n**%s %s**%n_%s_%n%s%n",
        repliedToMessage.getFrom().getPrettyName(),
        dateTime,
        text,
        dash);
  }
}
