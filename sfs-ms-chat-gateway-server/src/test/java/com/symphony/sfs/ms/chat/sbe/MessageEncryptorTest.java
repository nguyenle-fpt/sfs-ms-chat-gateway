package com.symphony.sfs.ms.chat.sbe;

import com.symphony.security.exceptions.CiphertextTransportVersionException;
import com.symphony.security.exceptions.SymphonyEncryptionException;
import com.symphony.security.exceptions.SymphonyInputException;
import com.symphony.security.helper.KeyIdentifier;
import com.symphony.sfs.ms.chat.datafeed.ContentKeyManager;
import com.symphony.sfs.ms.chat.datafeed.SBEEventMessage;
import com.symphony.sfs.ms.chat.datafeed.SBEEventUser;
import com.symphony.sfs.ms.chat.exception.ContentKeyRetrievalException;
import com.symphony.sfs.ms.chat.exception.EncryptionException;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.stubbing.Answer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class MessageEncryptorTest {
  private MessageEncryptor messageEncryptor;
  private SBEEventMessage parentMessage;
  private ContentKeyManager contentKeyManager;
  @Before
  public void setup() throws SymphonyInputException, CiphertextTransportVersionException, SymphonyEncryptionException {
    contentKeyManager = mock(ContentKeyManager.class);
    messageEncryptor = spy(new MessageEncryptor(contentKeyManager));

    parentMessage = SBEEventMessage.builder()
      .messageId("uXUfu2rsJRLALM0okkK1q3///oOAYQiRbQ==")
      .from(SBEEventUser.builder()
        .prettyNameNormalized("USER 1")
        .prettyName("User 1")
        .build())
      .text("parent message text")
      .ingestionDate(1634118131913L)
      .build();
  }
  @Test(expected = EncryptionException.class)
  public void encrypt_shouldThrowEncryptionException() throws EncryptionException, UnknownDatafeedUserException, ContentKeyRetrievalException {
    when(contentKeyManager.getContentKeyIdentifier(any(String.class), any(String.class))).thenThrow(new ContentKeyRetrievalException("threaId", "userId", 0L));
    messageEncryptor.encrypt("123456789", "streamId", "new message text", parentMessage);
  }

  @Test
  public void encrypt_shouldPutEncryptedInfoIntoMessage() throws EncryptionException, UnknownDatafeedUserException, ContentKeyRetrievalException {
    when(contentKeyManager.getContentKeyIdentifier(any(String.class), any(String.class))).thenReturn(new KeyIdentifier("FrgZb_0yPjOuShqA35oAM3___oOQU772dA".getBytes(), 123456789L, 0L));
    doAnswer((Answer<String>) invocation -> {
      Object[] args = invocation.getArguments();
      String content = invocation.getArgument(2);
      return String.format("encrypted**%s**", content);
    }).when(messageEncryptor).encrypt(Matchers.<byte[]>any(), any(KeyIdentifier.class), any(String.class));
    SBEEventMessage encryptedMessage = messageEncryptor.encrypt("123456789", "FrgZb_0yPjOuShqA35oAM3___oOQU772dA", "new message text", parentMessage);

    assertEquals(encryptedMessage.getText(), "encrypted****In reply to:**\n" +
      "**User 1 13/10/21 @ 11:42**\n" +
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
    assertEquals(encryptedMessage.getEntities().toString(), "{}");
    assertEquals(encryptedMessage.getEncryptedEntities(), "encrypted**{}**");
    assertEquals(encryptedMessage.getEncryptedMedia(), "encrypted**{\"content\":[],\"mediaType\":\"JSON\"}**");
    assertEquals(encryptedMessage.getCustomEntities(), "encrypted**[{\"type\":\"com.symphony.sharing.quote\",\"beginIndex\":0,\"endIndex\":79,\"data\":{\"version\":null,\"id\":null,\"messageId\":\"uXUfu2rsJRLALM0okkK1q3///oOAYQiRbQ==\",\"from\":{\"id\":null,\"username\":null,\"prettyNameNormalized\":\"USER 1\",\"emailAddress\":null,\"firstName\":null,\"surName\":null,\"prettyName\":\"User 1\",\"company\":null},\"ingestionDate\":1634118131913,\"chatType\":null,\"event\":null,\"streamId\":null,\"threadId\":null,\"text\":\"parent message text\",\"entityJSON\":null,\"presentationML\":null,\"disclaimer\":null,\"customEntities\":null,\"entities\":null,\"encryptedMedia\":null,\"encryptedEntities\":null,\"jsonMedia\":null,\"format\":null,\"parentMessageId\":null,\"parentRelationshipType\":null,\"encryptedFileKey\":null,\"msgFeatures\":0,\"metadata\":\"User 1 13/10/21 @ 11:42\",\"fromPod\":null,\"attributes\":null,\"attachments\":null,\"fileKeyEncryptedAttachments\":null,\"parsedCustomEntities\":[],\"formReply\":false,\"isBlast\":false,\"isChime\":false},\"version\":\"0.0.1\"}]**");
    assertEquals(encryptedMessage.getFormat(), "com.symphony.markdown");


  }
}
