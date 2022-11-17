package com.symphony.sfs.ms.chat.sbe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.symphony.security.exceptions.CiphertextTransportVersionException;
import com.symphony.security.exceptions.SymphonyEncryptionException;
import com.symphony.security.exceptions.SymphonyInputException;
import com.symphony.security.helper.KeyIdentifier;
import com.symphony.sfs.ms.chat.exception.EncryptionException;
import com.symphony.sfs.ms.starter.symphony.crypto.ContentKeyManager;
import com.symphony.sfs.ms.starter.symphony.crypto.exception.ContentKeyRetrievalException;
import com.symphony.sfs.ms.starter.symphony.crypto.exception.UnknownUserException;
import com.symphony.sfs.ms.starter.symphony.stream.EventUser;
import com.symphony.sfs.ms.starter.symphony.stream.MessageAttachment;
import com.symphony.sfs.ms.starter.symphony.stream.SBEEventMessage;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.stubbing.Answer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.symphony.sfs.ms.starter.testing.MockitoUtils.once;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MessageEncryptorTest {
  private MessageEncryptor messageEncryptor;
  private SBEEventMessage parentMessage;
  private List<MessageAttachment> msgAttachments;
  private ContentKeyManager contentKeyManager;
  @BeforeEach
  public void setup() throws SymphonyInputException, CiphertextTransportVersionException, SymphonyEncryptionException {
    contentKeyManager = mock(ContentKeyManager.class);
    messageEncryptor = spy(new MessageEncryptor(contentKeyManager));

    msgAttachments = Collections.singletonList(MessageAttachment.builder()
      .fileId("attachment_id")
      .name("attachment_name")
      .contentType("attachment_type")
      .sizeInBytes(1024L)
      .encrypted(false)
      .images(Collections.emptyMap())
      .build());
    parentMessage = SBEEventMessage.builder()
      .messageId("uXUfu2rsJRLALM0okkK1q3///oOAYQiRbQ==")
      .from(EventUser.builder()
        .prettyNameNormalized("USER 1")
        .prettyName("User 1")
        .build())
      .text("parent message text")
      .ingestionDate(1634118131913L)
      .build();
  }

  /////////////////////////////
  //// Build Reply Message ////
  /////////////////////////////

  @Test
  public void encrypt_shouldThrowEncryptionException() throws ContentKeyRetrievalException, UnknownUserException {
    when(contentKeyManager.getContentKeyIdentifier(any(String.class), any(String.class), anyString())).thenThrow(new ContentKeyRetrievalException("threaId", "userId", 0L));
    assertThrows( EncryptionException.class,
      () -> messageEncryptor.buildReplyMessage("123456789",  "userName", "streamId", "new message text", parentMessage, Collections.emptyList()));
  }

  @Test
  public void encrypt_shouldPutEncryptedInfoIntoMessage() throws EncryptionException, JsonProcessingException, com.symphony.sfs.ms.starter.symphony.crypto.exception.ContentKeyRetrievalException, UnknownUserException {
    KeyIdentifier keyIdentifier = new KeyIdentifier("FrgZb_0yPjOuShqA35oAM3___oOQU772dA".getBytes(), 123456789L, 0L);
    when(contentKeyManager.getContentKeyIdentifier(any(String.class), any(String.class), anyString())).thenReturn(keyIdentifier);
    doAnswer((Answer<String>) invocation -> {
      String content = invocation.getArgument(2);
      return String.format("encrypted**%s**", content);
    }).when(messageEncryptor).encrypt(ArgumentMatchers.<byte[]>any(), any(KeyIdentifier.class), any(String.class));
    SBEEventMessage encryptedMessage = messageEncryptor.buildReplyMessage("123456789", "userName", "FrgZb_0yPjOuShqA35oAM3___oOQU772dA", "new message text", parentMessage, Collections.emptyList());

    assertEquals(encryptedMessage.getText(), "encrypted****In reply to:**\n" +
      "**User 1 13/10/21 @ 09:42**\n" +
      "_parent message text_\n" +
      "———————————\n" +
      "new message text**");
    assertEquals(encryptedMessage.getThreadId(), "FrgZb/0yPjOuShqA35oAM3///oOQU772dA==");
    assertEquals(encryptedMessage.getParentRelationshipType(), "REPLY");
    assertEquals(encryptedMessage.getParentMessageId(), "uXUfu2rsJRLALM0okkK1q3///oOAYQiRbQ==");
    assertEquals(encryptedMessage.getMsgFeatures(), 3);
    assertEquals(encryptedMessage.getChatType(), "CHATROOM");
    assertEquals(encryptedMessage.getVersion(), "SOCIALMESSAGE");
    assertEquals(encryptedMessage.getEntityJSON(), "encrypted**{}**");
    assertEquals(encryptedMessage.getEntities(), Map.of("hashtags", new ArrayList<String>(), "userMentions", new ArrayList<String>(), "urls", new ArrayList<String>()));
    assertEquals(encryptedMessage.getEncryptedEntities(), "encrypted**{}**");
    assertEquals(encryptedMessage.getEncryptedMedia(), "encrypted**{\"content\":[],\"mediaType\":\"JSON\"}**");
    assertEquals("encrypted**[" +
      "{\"type\":\"com.symphony.sharing.quote\"," +
      "\"beginIndex\":0," +
      "\"endIndex\":79," +
      "\"data\":{" +
        "\"text\":\"parent message text\"," +
        "\"ingestionDate\":1634118131913," +
        "\"metadata\":\"User 1 13/10/21 @ 09:42\"," +
        "\"attachments\":[]," +
        "\"streamId\":null," +
        "\"id\":\"uXUfu2rsJRLALM0okkK1q3///oOAYQiRbQ==\"," +
        "\"presentationML\":null," +
        "\"entities\":{\"hashtags\":[],\"userMentions\":[],\"urls\":[]}," +
        "\"customEntities\":[]," +
        "\"entityJSON\":{}," +
        "\"jsonMedia\":[]" +
      "}," +
      "\"version\":\"0.0.1\"}]**",
      encryptedMessage.getCustomEntities());
    assertEquals(encryptedMessage.getFormat(), "com.symphony.markdown");
    verify(messageEncryptor, once()).generateSBEEventMessage(eq(keyIdentifier), eq(null), eq("123456789"), eq("FrgZb/0yPjOuShqA35oAM3///oOQU772dA=="),
      eq("**In reply to:**\n**User 1 13/10/21 @ 09:42**\n_parent message text_\n———————————\nnew message text"), eq(null), anyString(), eq(parentMessage), eq(null), eq(Collections.emptyList()));

  }

  @Test
  public void encrypt_shouldPutEncryptedInfoIntoMessage_attachments() throws EncryptionException, ContentKeyRetrievalException, JsonProcessingException, UnknownUserException {
    KeyIdentifier keyIdentifier = new KeyIdentifier("FrgZb_0yPjOuShqA35oAM3___oOQU772dA".getBytes(), 123456789L, 0L);
    when(contentKeyManager.getContentKeyIdentifier(any(String.class), any(String.class), anyString())).thenReturn(keyIdentifier);
    doAnswer((Answer<String>) invocation -> {
      String content = invocation.getArgument(2);
      return String.format("encrypted**%s**", content);
    }).when(messageEncryptor).encrypt(ArgumentMatchers.<byte[]>any(), any(KeyIdentifier.class), any(String.class));
    SBEEventMessage encryptedMessage = messageEncryptor.buildReplyMessage("123456789", "userName", "FrgZb_0yPjOuShqA35oAM3___oOQU772dA", "new message text", parentMessage, msgAttachments);

    assertEquals(encryptedMessage.getAttachments(), msgAttachments);
    assertEquals(encryptedMessage.getCustomEntities(), "encrypted**[" +
      "{\"type\":\"com.symphony.sharing.quote\"," +
      "\"beginIndex\":0,\"endIndex\":79," +
      "\"data\":{\"text\":\"parent message text\",\"ingestionDate\":1634118131913,\"metadata\":\"User 1 13/10/21 @ 09:42\",\"attachments\":[{\"fileId\":\"attachment_id\",\"name\":\"attachment_name\",\"encrypted\":false,\"sizeInBytes\":1024,\"images\":{},\"contentType\":\"attachment_type\"}],\"streamId\":null,\"id\":\"uXUfu2rsJRLALM0okkK1q3///oOAYQiRbQ==\",\"presentationML\":null,\"entities\":{\"hashtags\":[],\"userMentions\":[],\"urls\":[]},\"customEntities\":[],\"entityJSON\":{},\"jsonMedia\":[]}," +
      "\"version\":\"0.0.1\"}]" +
      "**");
    verify(messageEncryptor, once()).generateSBEEventMessage(eq(keyIdentifier), eq(null), eq("123456789"), eq("FrgZb/0yPjOuShqA35oAM3///oOQU772dA=="),
      eq("**In reply to:**\n**User 1 13/10/21 @ 09:42**\n_parent message text_\n———————————\nnew message text"), eq(null), anyString(), eq(parentMessage), eq(null), eq(msgAttachments));
  }

  @Test
  public void encrypt_shouldPutEncryptedInfoIntoMessage_emoji() throws EncryptionException, ContentKeyRetrievalException, JsonProcessingException, UnknownUserException {
    KeyIdentifier keyIdentifier = new KeyIdentifier("FrgZb_0yPjOuShqA35oAM3___oOQU772dA".getBytes(), 123456789L, 0L);
    when(contentKeyManager.getContentKeyIdentifier(any(String.class), any(String.class), anyString())).thenReturn(keyIdentifier);
    doAnswer((Answer<String>) invocation -> {
      String content = invocation.getArgument(2);
      return String.format("encrypted**%s**", content);
    }).when(messageEncryptor).encrypt(ArgumentMatchers.<byte[]>any(), any(KeyIdentifier.class), any(String.class));
    SBEEventMessage encryptedMessage = messageEncryptor.buildReplyMessage("123456789", "", "FrgZb_0yPjOuShqA35oAM3___oOQU772dA", "\uD83D\uDE00", parentMessage, Collections.emptyList());

    assertEquals(encryptedMessage.getText(), "encrypted****In reply to:**\n" +
      "**User 1 13/10/21 @ 09:42**\n" +
      "_parent message text_\n" +
      "———————————\n" +
      ":grinning:**");

    verify(messageEncryptor, once()).generateSBEEventMessage(eq(keyIdentifier), eq(null), eq("123456789"), eq("FrgZb/0yPjOuShqA35oAM3///oOQU772dA=="),
      eq("**In reply to:**\n**User 1 13/10/21 @ 09:42**\n_parent message text_\n———————————\n:grinning:"), eq(null), anyString(), eq(parentMessage), eq(null), eq(Collections.emptyList()));

  }

  @Test
  public void encrypt_shouldPutEncryptedInfoIntoMessage_replyToForwardedMessage() throws EncryptionException, ContentKeyRetrievalException, JsonProcessingException, UnknownUserException {
    KeyIdentifier keyIdentifier = new KeyIdentifier("FrgZb_0yPjOuShqA35oAM3___oOQU772dA".getBytes(), 123456789L, 0L);
    when(contentKeyManager.getContentKeyIdentifier(any(String.class), any(String.class), anyString())).thenReturn(keyIdentifier);
    doAnswer((Answer<String>) invocation -> {
      String content = invocation.getArgument(2);
      return String.format("encrypted**%s**", content);
    }).when(messageEncryptor).encrypt(ArgumentMatchers.<byte[]>any(), any(KeyIdentifier.class), any(String.class));

    String customEntities = "[\n" +
      "  {\n" +
      "    \"type\": \"com.symphony.sharing.message\",\n" +
      "    \"beginIndex\": 17,\n" +
      "    \"endIndex\": 140,\n" +
      "    \"data\": {}\n" +
      "  }\n" +
      "]";
    SBEEventMessage repliedMessage = parentMessage.toBuilder().text("New message text↵↵↵**Forwarded Message:**↵Posted by van in a private room, 11:13:24 am 11 Jan 2022:↵This is message used for forwarding test").customEntities(customEntities).build();
    SBEEventMessage encryptedMessage = messageEncryptor.buildReplyMessage("123456789", "", "FrgZb_0yPjOuShqA35oAM3___oOQU772dA", "Test reply to forwarded message", repliedMessage, Collections.emptyList());

    assertEquals(encryptedMessage.getText(), "encrypted****In reply to:**\n" +
      "**User 1 13/10/21 @ 09:42**\n" +
      "_New message text_\n" +
      "———————————\n" +
      "Test reply to forwarded message**");

    verify(messageEncryptor, once()).generateSBEEventMessage(eq(keyIdentifier), eq(null), eq("123456789"), eq("FrgZb/0yPjOuShqA35oAM3///oOQU772dA=="),
      eq("**In reply to:**\n**User 1 13/10/21 @ 09:42**\n_New message text_\n———————————\nTest reply to forwarded message"), eq(null), anyString(), eq(repliedMessage), eq(null), eq(Collections.emptyList()));

  }

  ///////////////////////////////
  //// Build Forward Message ////
  ///////////////////////////////

  @Test
  public void buildForwardedMessage_shouldThrowEncryptionException() throws ContentKeyRetrievalException, UnknownUserException {
    when(contentKeyManager.getContentKeyIdentifier(any(String.class), any(String.class), anyString())).thenThrow(new ContentKeyRetrievalException("threaId", "userId", 0L));
    assertThrows( EncryptionException.class,
      () -> messageEncryptor.buildForwardedMessage("123456789", "","streamId", "new message text", "Prefix", Collections.emptyList(), null));
  }

  @Test
  public void buildForwardedMessage_shouldBuildCorrectMessage() throws EncryptionException, ContentKeyRetrievalException, JsonProcessingException, UnknownUserException {
    KeyIdentifier keyIdentifier = new KeyIdentifier("FrgZb_0yPjOuShqA35oAM3___oOQU772dA".getBytes(), 123456789L, 0L);
    when(contentKeyManager.getContentKeyIdentifier(any(String.class), any(String.class), anyString())).thenReturn(keyIdentifier);
    doAnswer((Answer<String>) invocation -> {
      String content = invocation.getArgument(2);
      return String.format("encrypted**%s**", content);
    }).when(messageEncryptor).encrypt(ArgumentMatchers.<byte[]>any(), any(KeyIdentifier.class), any(String.class));
    var blastAttachments = Collections.singletonList(MessageAttachment.builder().fileId("attachment_id").contentType("image/png").build());
    var ephemeralKey = "ephemeral_key".getBytes(StandardCharsets.UTF_8);
    SBEEventMessage encryptedMessage = messageEncryptor.buildForwardedMessage("123456789", "","FrgZb_0yPjOuShqA35oAM3___oOQU772dA", "new message text", "from a WhatsApp chat\n", blastAttachments, ephemeralKey);

    assertEquals("encrypted**\n\n**Forwarded Message:**\n" +
      "from a WhatsApp chat\n" +
      "new message text**",
      encryptedMessage.getText());
    assertEquals(encryptedMessage.getThreadId(), "FrgZb/0yPjOuShqA35oAM3///oOQU772dA==");
    assertNull(encryptedMessage.getParentRelationshipType());
    assertEquals(encryptedMessage.getMsgFeatures(), 7);
    assertEquals(encryptedMessage.getChatType(), "CHATROOM");
    assertEquals(encryptedMessage.getVersion(), "SOCIALMESSAGE");
    assertEquals(encryptedMessage.getEntityJSON(), "encrypted**{}**");
    assertEquals(encryptedMessage.getEntities(), Map.of("hashtags", new ArrayList<String>(), "userMentions", new ArrayList<String>(), "urls", new ArrayList<String>()));
    assertEquals(encryptedMessage.getEncryptedEntities(), "encrypted**{}**");
    assertEquals(encryptedMessage.getEncryptedMedia(), "encrypted**{\"content\":[],\"mediaType\":\"JSON\"}**");
    assertEquals("encrypted**[{\"type\":\"com.symphony.sharing.message\"," +
        "\"beginIndex\":0,\"endIndex\":62," +
        "\"data\":{" +
          "\"text\":\"new message text\"," +
          "\"ingestionDate\":0," +
          "\"metadata\":\"from a WhatsApp chat\\n\"," +
          "\"attachments\":[]," +
          "\"streamId\":\"FrgZb/0yPjOuShqA35oAM3///oOQU772dA==\"," +
          "\"id\":null," +
          "\"presentationML\":null," +
          "\"entities\":{\"hashtags\":[],\"userMentions\":[],\"urls\":[]}," +
          "\"customEntities\":[]," +
          "\"entityJSON\":{}," +
          "\"jsonMedia\":[]}," +
          "\"version\":\"0.0.1\"" +
        "}]**",
      encryptedMessage.getCustomEntities());
    assertEquals(encryptedMessage.getFormat(), "com.symphony.markdown");
    assertEquals(blastAttachments, encryptedMessage.getFileKeyEncryptedAttachments());
    assertEquals(String.format("encrypted**%s**", Base64.encodeBase64String(ephemeralKey)), encryptedMessage.getEncryptedFileKey());

    verify(messageEncryptor, once()).generateSBEEventMessage(eq(keyIdentifier), eq(null), eq("123456789"), eq("FrgZb/0yPjOuShqA35oAM3///oOQU772dA=="),
      eq("\n\n**Forwarded Message:**\nfrom a WhatsApp chat\nnew message text"), eq(null), anyString(), eq(null), eq(null), eq(Collections.emptyList()));

  }

  @Test
  public void buildForwardedMessage_shouldPutEncryptedInfoIntoMessage_emoji() throws EncryptionException, ContentKeyRetrievalException, JsonProcessingException, UnknownUserException {
    KeyIdentifier keyIdentifier = new KeyIdentifier("FrgZb_0yPjOuShqA35oAM3___oOQU772dA".getBytes(), 123456789L, 0L);
    when(contentKeyManager.getContentKeyIdentifier(any(String.class), any(String.class), anyString())).thenReturn(keyIdentifier);
    doAnswer((Answer<String>) invocation -> {
      String content = invocation.getArgument(2);
      return String.format("encrypted**%s**", content);
    }).when(messageEncryptor).encrypt(ArgumentMatchers.<byte[]>any(), any(KeyIdentifier.class), any(String.class));
    SBEEventMessage encryptedMessage = messageEncryptor.buildForwardedMessage("123456789", "", "FrgZb_0yPjOuShqA35oAM3___oOQU772dA", "\uD83D\uDE00", "from a WhatsApp chat\n", Collections.emptyList(), null);

    assertEquals("encrypted**\n\n**Forwarded Message:**\n" +
      "from a WhatsApp chat\n" +
      ":grinning:**",
      encryptedMessage.getText());

    verify(messageEncryptor, once()).generateSBEEventMessage(eq(keyIdentifier), eq(null), eq("123456789"), eq("FrgZb/0yPjOuShqA35oAM3///oOQU772dA=="),
      eq("\n\n**Forwarded Message:**\nfrom a WhatsApp chat\n:grinning:"), eq(null), anyString(), eq(null), eq(null), eq(Collections.emptyList()));

  }

  ///////////////////////////////
  //// Build Contact Message ////
  ///////////////////////////////

  @Test
  public void buildContactMessage_shouldThrowEncryptionException() throws ContentKeyRetrievalException, UnknownUserException {
    when(contentKeyManager.getContentKeyIdentifier(any(String.class), any(String.class), anyString())).thenThrow(new ContentKeyRetrievalException("threaId", "userId", 0L));
    assertThrows( EncryptionException.class,
      () -> messageEncryptor.buildContactMessage("123456789", "","streamId", "text", "{ \"type\": \"unknownType\"}", "<div data-format=\"PresentationML\" data-version=\"2.0\">PresentationML Content</div>")
    );
  }

  @Test
  public void buildContactMessage_shouldBuildCorrectMessage() throws EncryptionException, ContentKeyRetrievalException, JsonProcessingException, UnknownUserException {
    KeyIdentifier keyIdentifier = new KeyIdentifier("FrgZb_0yPjOuShqA35oAM3___oOQU772dA".getBytes(), 123456789L, 0L);
    when(contentKeyManager.getContentKeyIdentifier(any(String.class), any(String.class), anyString())).thenReturn(keyIdentifier);
    doAnswer((Answer<String>) invocation -> {
      String content = invocation.getArgument(2);
      return String.format("encrypted**%s**", content);
    }).when(messageEncryptor).encrypt(ArgumentMatchers.<byte[]>any(), any(KeyIdentifier.class), any(String.class));
    SBEEventMessage encryptedMessage = messageEncryptor.buildContactMessage("123456789", "","FrgZb_0yPjOuShqA35oAM3___oOQU772dA", "text", "{ \"type\": \"send_contacts\"}", "<div data-format=\"PresentationML\" data-version=\"2.0\">PresentationML Content</div>");

    assertEquals("encrypted**text**", encryptedMessage.getText());
    assertEquals(encryptedMessage.getThreadId(), "FrgZb/0yPjOuShqA35oAM3///oOQU772dA==");
    assertNull(encryptedMessage.getParentRelationshipType());
    assertEquals(encryptedMessage.getMsgFeatures(), 3);
    assertEquals(encryptedMessage.getChatType(), "CHATROOM");
    assertEquals(encryptedMessage.getVersion(), "SOCIALMESSAGE");
    assertEquals(encryptedMessage.getEntityJSON(), "encrypted**{ \"type\": \"send_contacts\"}**");
    assertEquals(encryptedMessage.getEntities(), Map.of("hashtags", new ArrayList<String>(), "userMentions", new ArrayList<String>(), "urls", new ArrayList<String>()));
    assertEquals(encryptedMessage.getEncryptedEntities(), "encrypted**{}**");
    assertEquals(encryptedMessage.getEncryptedMedia(), "encrypted**{\"content\":[],\"mediaType\":\"JSON\"}**");
    assertNull(encryptedMessage.getCustomEntities());
    assertEquals(encryptedMessage.getFormat(), "com.symphony.messageml.v2");
    assertNull(encryptedMessage.getFileKeyEncryptedAttachments());
    assertNull(encryptedMessage.getEncryptedFileKey());

    verify(messageEncryptor, once()).generateSBEEventMessage(eq(keyIdentifier), eq(null), eq("123456789"), eq("FrgZb/0yPjOuShqA35oAM3///oOQU772dA=="),
      eq("text"), eq("<div data-format=\"PresentationML\" data-version=\"2.0\">PresentationML Content</div>"), eq(null), eq(null), eq("{ \"type\": \"send_contacts\"}"), eq(Collections.emptyList()));

  }

}
