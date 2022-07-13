package com.symphony.sfs.ms.chat.datafeed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.symphony.oss.canon.runtime.ModelRegistry;
import com.symphony.oss.commons.dom.json.MutableJsonObject;
import com.symphony.oss.commons.immutable.ImmutableByteArray;
import com.symphony.oss.models.chat.canon.LiveCurrentMessageType;
import com.symphony.oss.models.chat.canon.SocialMessageEntity;
import com.symphony.oss.models.chat.canon.facade.SocialMessage;
import com.symphony.oss.models.chat.canon.facade.User;
import com.symphony.oss.models.core.canon.facade.ThreadId;
import com.symphony.security.clientsdk.transport.CiphertextFactory;
import com.symphony.security.clientsdk.transport.CiphertextTransportV4;
import com.symphony.security.exceptions.CiphertextTransportIsEmptyException;
import com.symphony.security.exceptions.CiphertextTransportVersionException;
import com.symphony.security.exceptions.InvalidDataException;
import com.symphony.security.exceptions.SymphonyEncryptionException;
import com.symphony.security.exceptions.SymphonyInputException;
import com.symphony.security.helper.IClientCryptoHandler;
import com.symphony.security.helper.KeyIdentifier;
import com.symphony.sfs.ms.chat.exception.DecryptionException;
import com.symphony.sfs.ms.starter.symphony.crypto.ContentKeyManager;
import com.symphony.sfs.ms.starter.symphony.crypto.exception.ContentKeyRetrievalException;
import com.symphony.sfs.ms.starter.symphony.crypto.exception.UnknownUserException;
import com.symphony.sfs.ms.starter.symphony.stream.SBEEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class MessageDecryptorTest {
  private ContentKeyManager contentKeyManager;
  private IClientCryptoHandler cryptoHandler;
  private MessageDecryptor messageDecryptor;

  @BeforeEach
  public void setup() throws SymphonyInputException, CiphertextTransportVersionException, SymphonyEncryptionException {
    contentKeyManager = mock(ContentKeyManager.class);
    cryptoHandler = mock(IClientCryptoHandler.class);

    messageDecryptor = new MessageDecryptor(contentKeyManager, new ObjectMapper());
    ReflectionTestUtils.setField(messageDecryptor, "cryptoHandler", cryptoHandler);
  }

  private String mockEncryptText(String text){
    return String.format("encrypted**%s**", text);
  }

  private String mockDecryptMsg(String message) throws InvalidDataException {
    String pattern = "(encrypted[*]{2})([\\s\\S]*)([*]{2})";
    Pattern r = Pattern.compile(pattern);
    Matcher m = r.matcher(message);

    if (m.find( )) {
      return m.group(2);
    } else {
      throw new InvalidDataException();
    }
  }

  @Test
  void decryptSBEMessage() throws ContentKeyRetrievalException, UnknownUserException, CiphertextTransportIsEmptyException, InvalidDataException, SymphonyInputException, CiphertextTransportVersionException, SymphonyEncryptionException, DecryptionException {
      KeyIdentifier keyIdentifier = new KeyIdentifier("FrgZb_0yPjOuShqA35oAM3___oOQU772dA".getBytes(), 123456789L, 0L);
      when(contentKeyManager.getContentKeyIdentifier(any(String.class), any(String.class), anyString())).thenReturn(keyIdentifier);

      try(MockedStatic<CiphertextFactory> utilities = mockStatic(CiphertextFactory.class)) {
        utilities.when(() -> CiphertextFactory.getTransport(anyString())).thenReturn(new CiphertextTransportV4());
        doAnswer((Answer<String>) invocation -> {
          String message = invocation.getArgument(1);
          return mockDecryptMsg(message);
        }).when(cryptoHandler).decryptMsg(ArgumentMatchers.<byte[]>any(), any(String.class));

        SBEEventMessage encryptedMessage = SBEEventMessage.builder()
          .messageId("uXUfu2rsJRLALM0okkK1q3///oOAYQiRbQ==").threadId("FrgZb_0yPjOuShqA35oAM3___oOQU772dA").text(mockEncryptText("Test text")).presentationML(mockEncryptText("Test presentationML"))
          .customEntities(mockEncryptText("[{\"type\":\"com.symphony.sharing.quote\"," +
          "\"beginIndex\":0," +
          "\"endIndex\":79," +
          "\"data\":{" +
          "\"text\":\"parent message text\"," +
          "\"ingestionDate\":1657584000000," +
          "\"metadata\":\"User 1 12/07/22 @ 09:42\"," +
          "\"attachments\":[]," +
          "\"streamId\":null," +
          "\"id\":\"uXUfu2rsJRLALM0okkK1q3///oOAYQiRbQ==\"," +
          "\"presentationML\":null," +
          "\"entities\":{\"hashtags\":[],\"userMentions\":[],\"urls\":[]}," +
          "\"customEntities\":[]," +
          "\"entityJSON\":{}," +
          "\"jsonMedia\":[]" +
          "}," +
          "\"version\":\"0.0.1\"}]"))
          .entityJSON(mockEncryptText("{}"))
          .encryptedMedia(mockEncryptText("{\"mediaType\":\"JSON\", \"content\":[{\"type\":\"excel-rcp\",\"title\":\"\",\"text\":[[\"ID\",\"Type\"],[\"CES-7754\",\"Story\"]]}]}"))
          .encryptedEntities(mockEncryptText("{}")).build();

        assertEquals("encrypted**Test text**", encryptedMessage.getText());

        messageDecryptor.decrypt(encryptedMessage, "123456789", "userName");

        assertEquals("Test presentationML", encryptedMessage.getPresentationML());
        assertEquals("Test text", encryptedMessage.getText());
        assertEquals("{}", encryptedMessage.getEntityJSON());
        assertEquals("[{\"type\":\"com.symphony.sharing.quote\"," +
          "\"beginIndex\":0," +
          "\"endIndex\":79," +
          "\"data\":{" +
          "\"text\":\"parent message text\"," +
          "\"ingestionDate\":1657584000000," +
          "\"metadata\":\"User 1 12/07/22 @ 09:42\"," +
          "\"attachments\":[]," +
          "\"streamId\":null," +
          "\"id\":\"uXUfu2rsJRLALM0okkK1q3///oOAYQiRbQ==\"," +
          "\"presentationML\":null," +
          "\"entities\":{\"hashtags\":[],\"userMentions\":[],\"urls\":[]}," +
          "\"customEntities\":[]," +
          "\"entityJSON\":{}," +
          "\"jsonMedia\":[]" +
          "}," +
          "\"version\":\"0.0.1\"}]", encryptedMessage.getCustomEntities());
        assertEquals("{\"mediaType\":\"JSON\", \"content\":[{\"type\":\"excel-rcp\",\"title\":\"\",\"text\":[[\"ID\",\"Type\"],[\"CES-7754\",\"Story\"]]}]}",
          encryptedMessage.getEncryptedMedia());
        assertEquals("[{\"type\":\"excel-rcp\",\"title\":\"\",\"text\":[[\"ID\",\"Type\"],[\"CES-7754\",\"Story\"]]}]", encryptedMessage.getJsonMedia());
        assertEquals("{}", encryptedMessage.getEncryptedEntities());
      }
  }

  @Test
  void decryptSocialMessage() throws ContentKeyRetrievalException, UnknownUserException, CiphertextTransportIsEmptyException, InvalidDataException, SymphonyInputException, CiphertextTransportVersionException, SymphonyEncryptionException, DecryptionException {
    KeyIdentifier keyIdentifier = new KeyIdentifier("FrgZb_0yPjOuShqA35oAM3___oOQU772dA".getBytes(), 123456789L, 0L);
    when(contentKeyManager.getContentKeyIdentifier(any(String.class), any(String.class), anyString())).thenReturn(keyIdentifier);

    try(MockedStatic<CiphertextFactory> utilities = mockStatic(CiphertextFactory.class)) {
      utilities.when(() -> CiphertextFactory.getTransport(anyString())).thenReturn(new CiphertextTransportV4());
      doAnswer((Answer<String>) invocation -> {
        String message = invocation.getArgument(1);
        return mockDecryptMsg(message);
      }).when(cryptoHandler).decryptMsg(ArgumentMatchers.<byte[]>any(), any(String.class));


      SocialMessage encryptedMessage = (SocialMessage) new SocialMessageEntity.Builder().withVersion(LiveCurrentMessageType.SOCIALMESSAGE.toString())
        .withMessageId(ImmutableByteArray.newInstance("uXUfu2rsJRLALM0okkK1q3///oOAYQiRbQ==")).withIngestionDate(Long.valueOf("1657584000000"))
        .withThreadId(ThreadId.newBuilder().build("FrgZb_0yPjOuShqA35oAM3___oOQU772dA")).withText(mockEncryptText("Test text")).withPresentationML(mockEncryptText("Test presentationML"))
        .build();

      assertEquals("encrypted**Test text**", encryptedMessage.getText());

      GatewaySocialMessage gatewaySocialMessage = GatewaySocialMessage.builder().members(new ArrayList<>()).fromUser(new User(new MutableJsonObject(), new ModelRegistry())).build();
      messageDecryptor.decrypt(encryptedMessage, "123456789", "userName", gatewaySocialMessage);

      assertEquals("Test text", gatewaySocialMessage.getTextContent());
      assertEquals("Test presentationML", gatewaySocialMessage.getPresentationMLContent());
    }
  }
}
