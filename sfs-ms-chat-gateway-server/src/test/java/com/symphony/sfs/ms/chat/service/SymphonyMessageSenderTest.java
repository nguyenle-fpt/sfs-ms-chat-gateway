package com.symphony.sfs.ms.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.symphony.sfs.ms.chat.api.util.AbstractIntegrationTest;
import com.symphony.sfs.ms.chat.datafeed.MessageDecryptor;
import com.symphony.sfs.ms.chat.exception.DecryptionException;
import com.symphony.sfs.ms.chat.exception.EncryptionException;
import com.symphony.sfs.ms.chat.exception.InlineReplyMessageException;
import com.symphony.sfs.ms.chat.generated.model.AttachmentInfo;
import com.symphony.sfs.ms.chat.generated.model.MessageInfoWithCustomEntities;
import com.symphony.sfs.ms.chat.generated.model.SendMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.SymphonyAttachment;
import com.symphony.sfs.ms.chat.mapper.MessageInfoMapper;
import com.symphony.sfs.ms.chat.mapper.MessageInfoMapperImpl;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.sbe.MessageEncryptor;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyService;
import com.symphony.sfs.ms.chat.util.SymphonySystemMessageTemplateProcessor;
import com.symphony.sfs.ms.starter.security.SessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonyRsaAuthFunction;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.CustomEntity;
import com.symphony.sfs.ms.starter.symphony.stream.MessageAttachment;
import com.symphony.sfs.ms.starter.symphony.stream.SBEEventMessage;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.context.MessageSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseToSymphony.BLAST_ATTACHMENTS_UPLOAD_FAILED;
import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseToSymphony.ENCRYPTION_FAILED;
import static com.symphony.sfs.ms.starter.util.RsaUtils.parseRSAPrivateKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SymphonyMessageSenderTest extends AbstractIntegrationTest {
  private SymphonyMessageSender symphonyMessageSender;

  private SymphonySystemMessageTemplateProcessor templateProcessor;
  private MessageIOMonitor messageMetrics;
  private MessageDecryptor messageDecryptor;
  private MessageEncryptor messageEncryptor;
  private SymphonyService symphonyService;
  private SessionSupplier<SymphonySession> userSession;


  @BeforeEach
  public void setUp(MessageSource messageSource) throws Exception {
    templateProcessor = mock(SymphonySystemMessageTemplateProcessor.class);
    messageMetrics = mock(MessageIOMonitor.class);
    messageEncryptor = mock(MessageEncryptor.class);
    messageDecryptor = mock(MessageDecryptor.class);
    symphonyService = mock(SymphonyService.class);
    MessageInfoMapper messageInfoMapper = new MessageInfoMapperImpl();
    symphonyMessageSender = new SymphonyMessageSender(podConfiguration, datafeedSessionPool, federatedAccountRepository, streamService, templateProcessor, messageMetrics, messageEncryptor, messageDecryptor, symphonyService, empSchemaService, messageSource, messageInfoMapper, objectMapper);
    userSession = new SessionSupplier<>("username", new SymphonyRsaAuthFunction(authenticationService, podConfiguration, parseRSAPrivateKey(chatConfiguration.getSharedPrivateKey().getData())));
  }


  ////////////////////
  //// Send Reply ////
  ////////////////////


  @Test
  public void sendReply_FederatedUserNotFound() {
    assertThrows(SendMessageFailedProblem.class, () -> {
      symphonyMessageSender.sendReplyMessage("streamId", "123456789", "message text", "message_id", false, Optional.empty());
    });
  }

  @Test
  public void sendReply_DecryptionEncryptionProblem() throws EncryptionException {
    federatedAccountRepository.save(FederatedAccount.builder().symphonyUserId("123456789").symphonyUsername("wa_bot_user_name").build());

    SBEEventMessage parentMessage = SBEEventMessage.builder().build();
    when(symphonyService.getEncryptedMessage(eq("message_id"), any(SessionSupplier.class))).thenReturn(Optional.of(parentMessage));
    when(messageEncryptor.buildReplyMessage("123456789", "wa_bot_user_name", "streamId", "message text", parentMessage, Collections.emptyList())).thenThrow(new EncryptionException(null));
    Optional<MessageInfoWithCustomEntities> newMessage = symphonyMessageSender.sendReplyMessage("streamId", "123456789", "message text", "message_id", false, Optional.empty());

    verify(messageMetrics, times(1)).onMessageBlockToSymphony(ENCRYPTION_FAILED, "streamId");
    assertTrue(newMessage.isEmpty());
  }

  @Test
  public void sendReplyMessageSuccesfully_AttachmentNotSupported() throws EncryptionException, JsonProcessingException {
    federatedAccountRepository.save(FederatedAccount.builder().symphonyUserId("123456789").symphonyUsername("wa_bot_user_name").build());

    SBEEventMessage parentMessage = SBEEventMessage.builder()
      .messageId("message_id")
      .attachments(Collections.singletonList(MessageAttachment.builder().fileId("attachment_id").build()))
      .build();
    when(symphonyService.getEncryptedMessage(any(String.class), any(SessionSupplier.class))).thenReturn(Optional.of(parentMessage));

    assertThrows(InlineReplyMessageException.class, () -> {
      symphonyMessageSender.sendReplyMessage("streamId", "123456789", "message text", "message_id", false, Optional.empty());
    });

    assertThrows(InlineReplyMessageException.class, () -> {
      symphonyMessageSender.sendReplyMessage("streamId", "123456789", "message text", "message_id", false,
        Optional.of(Collections.singletonList("attachment_message_id")));
    });

  }

  @Test
  public void sendReplyMessageSuccesfully_Text() throws EncryptionException, JsonProcessingException {
    federatedAccountRepository.save(FederatedAccount.builder().symphonyUserId("123456789").symphonyUsername("wa_bot_user_name").build());

    SBEEventMessage parentMessage = SBEEventMessage.builder().build();
    when(symphonyService.getEncryptedMessage(eq("message_id"), any(SessionSupplier.class))).thenReturn(Optional.of(parentMessage));
    SBEEventMessage messageToBeSent = SBEEventMessage.builder().build();
    when(messageEncryptor.buildReplyMessage("123456789", "wa_bot_user_name", "streamId", "message text", parentMessage, Collections.emptyList())).thenReturn(messageToBeSent);
    when(symphonyService.sendReplyMessage(eq(messageToBeSent), any(SessionSupplier.class))).thenReturn(SBEEventMessage.builder().messageId("NxqDE3jYX/ePoCu+ytgVXH///oOG+B9FdA==").build());

    Optional<MessageInfoWithCustomEntities> newMessage = symphonyMessageSender.sendReplyMessage("streamId", "123456789", "message text", "message_id", false, Optional.empty());

    verify(messageMetrics, times(1)).onSendMessageToSymphony("123456789", "streamId");
    assertEquals(newMessage.get().getMessageId(), "NxqDE3jYX_ePoCu-ytgVXH___oOG-B9FdA");

  }

  @Test
  public void sendReplyMessageSuccesfully_Attachments() throws EncryptionException, JsonProcessingException {
    federatedAccountRepository.save(FederatedAccount.builder().symphonyUserId("123456789").symphonyUsername("wa_bot_user_name").build());

    MessageAttachment parentMessageAttachment = MessageAttachment.builder().fileId("attachment_1").build();
    SBEEventMessage parentMessage = SBEEventMessage.builder()
      .messageId("message_id")
      .attachments(Collections.singletonList(parentMessageAttachment))
      .build();

    MessageAttachment addedAttachment = MessageAttachment.builder().fileId("attachment_2").build();
    SBEEventMessage attachmentMessage = SBEEventMessage.builder()
      .messageId("attachment_message_id")
      .attachments(Collections.singletonList(addedAttachment))
      .build();

    doAnswer(invocation -> {
      String messageId = invocation.getArgument(0);
      return StringUtils.equals(messageId, "message_id") ? Optional.of(parentMessage) : Optional.of(attachmentMessage);
    }).when(symphonyService).getEncryptedMessage(any(String.class), any(SessionSupplier.class));

    doAnswer(invocation -> invocation.getArgument(4)).when(messageEncryptor).buildReplyMessage(anyString(), anyString(), anyString(), anyString(), any(SBEEventMessage.class), any(List.class));

    when(symphonyService.sendReplyMessage(any(SBEEventMessage.class), any(SessionSupplier.class))).thenReturn(SBEEventMessage.builder().messageId("NxqDE3jYX/ePoCu+ytgVXH///oOG+B9FdA==").build());

    Optional<MessageInfoWithCustomEntities> newMessage = symphonyMessageSender.sendReplyMessage("streamId", "123456789", "message text", "message_id", true,
      Optional.of(Collections.singletonList("attachment_message_id")));

    verify(symphonyService, times(1)).getEncryptedMessage(eq("message_id"), any(SessionSupplier.class));
    verify(symphonyService, times(1)).getEncryptedMessage(eq("attachment_message_id"), any(SessionSupplier.class));
    verify(messageEncryptor, times(1)).buildReplyMessage("123456789", "wa_bot_user_name", "streamId", "message text", parentMessage, List.of(parentMessageAttachment, addedAttachment));
    verify(messageMetrics, times(1)).onSendMessageToSymphony("123456789", "streamId");
    assertEquals(newMessage.get().getMessageId(), "NxqDE3jYX_ePoCu-ytgVXH___oOG-B9FdA");

  }

  @Test
  public void sendReplyToHistoricMessage() throws EncryptionException, JsonProcessingException {
    federatedAccountRepository.save(FederatedAccount.builder().symphonyUserId("123456789").symphonyUsername("wa_bot_user_name").build());

    SBEEventMessage parentMessage = SBEEventMessage.builder().build();
    when(symphonyService.getEncryptedMessage(eq("message_id"), any(SessionSupplier.class))).thenAnswer((Answer<Optional<SBEEventMessage>>) invocation -> {
        SessionSupplier<SymphonySession> session = invocation.getArgument(1);
        if ("bot".equals(session.getPrincipal())) {
          return Optional.of(parentMessage);
        } else {
          throw new RuntimeException();
        }
    });
    SBEEventMessage messageToBeSent = SBEEventMessage.builder().build();
    when(messageEncryptor.buildReplyMessage("123456789", "wa_bot_user_name", "streamId", "message text", parentMessage, Collections.emptyList())).thenReturn(messageToBeSent);
    when(symphonyService.sendReplyMessage(eq(messageToBeSent), any(SessionSupplier.class))).thenReturn(SBEEventMessage.builder().messageId("NxqDE3jYX/ePoCu+ytgVXH///oOG+B9FdA==").build());

    Optional<MessageInfoWithCustomEntities> newMessage = symphonyMessageSender.sendReplyMessage("streamId", "123456789", "message text", "message_id", false, Optional.empty());

    verify(symphonyService, times(2)).getEncryptedMessage(any(String.class), any(SessionSupplier.class));
    assertEquals(newMessage.get().getMessageId(), "NxqDE3jYX_ePoCu-ytgVXH___oOG-B9FdA");

  }


  //////////////////////
  //// Send forward ////
  //////////////////////


  @Test
  public void sendForwardedMessage_text_works() throws EncryptionException, JsonProcessingException {
    federatedAccountRepository.save(FederatedAccount.builder().symphonyUserId("123456789").symphonyUsername("wa_bot_user_name").emp("WHATSAPP").build());
    SBEEventMessage messageToBeSent = SBEEventMessage.builder().build();
    when(messageEncryptor.buildForwardedMessage(eq("123456789"), eq("wa_bot_user_name"),eq("streamId"), eq("message text"), eq("from WHATSAPP\n"), eq(Collections.emptyList()), any(byte[].class))).thenReturn(messageToBeSent);
    when(symphonyService.sendBulkMessage(eq(messageToBeSent), any(SessionSupplier.class))).thenReturn(SBEEventMessage.builder().messageId("NxqDE3jYX/ePoCu+ytgVXH///oOG+B9FdA==").build());

    Optional<MessageInfoWithCustomEntities> newMessage = symphonyMessageSender.sendForwardedMessage("streamId", "123456789", "message text", Collections.emptyList());

    verify(messageMetrics, times(1)).onSendMessageToSymphony("123456789", "streamId");
    assertEquals(newMessage.get().getMessageId(), "NxqDE3jYX_ePoCu-ytgVXH___oOG-B9FdA");

  }

  @Test
  public void sendForwardedMessage_withAttachments_works() throws EncryptionException, JsonProcessingException {
    federatedAccountRepository.save(FederatedAccount.builder().symphonyUserId("123456789").symphonyUsername("wa_bot_user_name").emp("WHATSAPP").build());
    SBEEventMessage messageToBeSent = SBEEventMessage.builder().build();
    MessageAttachment[] uploadedAttachments = { MessageAttachment.builder().fileId("new_attachment_id").build() };
    when(messageEncryptor.buildForwardedMessage(eq("123456789"), eq("wa_bot_user_name"),eq("streamId"), eq("message text"), eq("from WHATSAPP\n"), eq(Arrays.asList(uploadedAttachments)), any(byte[].class))).thenReturn(messageToBeSent);
    when(symphonyService.sendBulkMessage(eq(messageToBeSent), any(SessionSupplier.class))).thenReturn(SBEEventMessage.builder().messageId("NxqDE3jYX/ePoCu+ytgVXH///oOG+B9FdA==").build());
    when(symphonyService.uploadBlastAttachment(any(SessionSupplier.class), any(String.class), any(String.class), any(byte[].class))).thenReturn(uploadedAttachments);

    Optional<MessageInfoWithCustomEntities> newMessage = symphonyMessageSender.sendForwardedMessage("streamId", "123456789", "message text", Collections.singletonList(new SymphonyAttachment().fileName("file_name").contentType("image/png").data("data")));

    verify(messageMetrics, times(1)).onSendMessageToSymphony("123456789", "streamId");
    assertEquals(newMessage.get().getMessageId(), "NxqDE3jYX_ePoCu-ytgVXH___oOG-B9FdA");

  }

  @Test
  public void sendForwardedMessage_withAttachments_failedUploadBlastAttachment() throws EncryptionException, JsonProcessingException {
    federatedAccountRepository.save(FederatedAccount.builder().symphonyUserId("123456789").symphonyUsername("wa_bot_user_name").emp("WHATSAPP").build());
    when(symphonyService.uploadBlastAttachment(any(SessionSupplier.class), any(String.class), any(String.class), any(byte[].class))).thenReturn(null);


    Optional<MessageInfoWithCustomEntities> newMessage = symphonyMessageSender.sendForwardedMessage("streamId", "123456789", "message text", Collections.singletonList(new SymphonyAttachment().fileName("file_name")));
    assertTrue(newMessage.isEmpty());
    verify(messageMetrics, times(1)).onMessageBlockToSymphony(BLAST_ATTACHMENTS_UPLOAD_FAILED, "streamId");

  }

  @Test
  public void sendForwardedMessage_withAttachments_failedEncryptAttachment() throws EncryptionException, JsonProcessingException {
    federatedAccountRepository.save(FederatedAccount.builder().symphonyUserId("123456789").symphonyUsername("wa_bot_user_name").emp("WHATSAPP").build());

    // attachment without data could make encryption failed
    Optional<MessageInfoWithCustomEntities> newMessage = symphonyMessageSender.sendForwardedMessage("streamId", "123456789", "message text", Collections.singletonList(new SymphonyAttachment().fileName("file_name").contentType("image/png").data(null)));

    assertTrue(newMessage.isEmpty());
    verify(messageMetrics, times(1)).onMessageBlockToSymphony(BLAST_ATTACHMENTS_UPLOAD_FAILED, "streamId");

  }

  @Test
  public void sendForwardedMessage_noFederatedAccount() throws EncryptionException, JsonProcessingException {
    assertThrows(SendMessageFailedProblem.class, () -> {
      symphonyMessageSender.sendForwardedMessage("streamId", "123456789", "message text", Collections.emptyList());
    });
  }

  @Test
  public void sendForward_DecryptionEncryptionProblem() throws EncryptionException {
    federatedAccountRepository.save(FederatedAccount.builder().symphonyUserId("123456789").symphonyUsername("wa_bot_user_name").emp("WHATSAPP").build());

    when(messageEncryptor.buildForwardedMessage(eq("123456789"), eq("wa_bot_user_name"), eq("streamId"), eq("message text"), eq("from WHATSAPP\n"), eq(Collections.emptyList()), any(byte[].class))).thenThrow(new EncryptionException(null));

    Optional<MessageInfoWithCustomEntities> newMessage = symphonyMessageSender.sendForwardedMessage("streamId", "123456789", "message text", Collections.emptyList());

    verify(messageMetrics, times(1)).onMessageBlockToSymphony(ENCRYPTION_FAILED, "streamId");
    assertTrue(newMessage.isEmpty());
  }

  @Test
  public void decryptAndBuildMessageInfo_WithQuote() throws DecryptionException, JsonProcessingException {
    String symphonyUserId = "123456789";
    String messageId = "messageId1///n1ZVrIxbQ==";

    String customEntities = "[\n" +
      "  {\n" +
      "    \"type\": \"com.symphony.sharing.quote\",\n" +
      "    \"beginIndex\": 0,\n" +
      "    \"endIndex\": 13,\n" +
      "    \"data\": {\n" +
      "      \"attachments\": [\n" +
      "        {\n" +
      "          \"fileId\": \"external_13469017566287%2FEwfQ5eSXxpi8DArTh4cFiQ%3D%3D\",\n" +
      "          \"name\": \"55c0edab9f685bfe.JPG\",\n" +
      "          \"encrypted\": true,\n" +
      "          \"sizeInBytes\": 62412,\n" +
      "          \"images\": {\n" +
      "            \"600\": \"external_13469017566287%2FE36ZvDh6pA%2BAjFaI81ETzw%3D%3D\"\n" +
      "          },\n" +
      "          \"contentType\": \"image/jpeg\"\n" +
      "        }\n" +
      "      ],\n" +
      "     \"id\": \"messageId1///n1ZVrIxbQ==\"" +
      "    }\n" +
      "  }\n" +
      "]";
    List<CustomEntity> parsedCustomEntities = Arrays.asList(objectMapper.readValue(customEntities, CustomEntity[].class));
    SBEEventMessage sbeEventMessage1 = SBEEventMessage.builder().messageId(messageId).text("Message").ingestionDate(1665413611L).build();
    SBEEventMessage sbeEventMessage2 = SBEEventMessage.builder()
      .messageId("messageId2///n2ZVrIxbQ==")
      .text("In reply to: New message")
      .ingestionDate(1665413648L)
      .customEntities(customEntities)
      .parsedCustomEntities(parsedCustomEntities)
      .build();
    SBEEventMessage sbeEventMessage3 = SBEEventMessage.builder()
      .messageId("messageId3///n3ZVrIxbQ==")
      .text("message with attachment")
      .ingestionDate(1665413648L)
      .attachments(List.of(MessageAttachment.builder().fileId("fileId").name("fileName.pdf").contentType("application/pdf").sizeInBytes(123L).images(Map.of("600", "fileId600")).build()))
      .build();
    Map<String, SBEEventMessage> retrievedMessage = new HashMap<>();
    retrievedMessage.put("messageId1___n1ZVrIxbQ", sbeEventMessage1);
    retrievedMessage.put("messageId2___n2ZVrIxbQ", sbeEventMessage2);

    doNothing().when(messageDecryptor).decrypt(any(SBEEventMessage.class), eq(symphonyUserId), eq(userSession.getPrincipal()));

    MessageInfoWithCustomEntities messageInfoWithCustomEntities1 = symphonyMessageSender.decryptAndBuildMessageInfo(sbeEventMessage2, symphonyUserId, userSession, retrievedMessage);
    MessageInfoWithCustomEntities messageInfoWithCustomEntities2 = symphonyMessageSender.decryptAndBuildMessageInfo(sbeEventMessage3, symphonyUserId, userSession, retrievedMessage);
    assertEquals(new MessageInfoWithCustomEntities()
      .messageId("messageId2___n2ZVrIxbQ")
      .timestamp(1665413648L)
      .parentMessage(new MessageInfoWithCustomEntities().messageId("messageId1___n1ZVrIxbQ").message("Message").timestamp(1665413611L))
      .message("New message")
      .customEntities(customEntities)
      .attachments(List.of(new AttachmentInfo().id("external_13469017566287%2FEwfQ5eSXxpi8DArTh4cFiQ%3D%3D").fileName("55c0edab9f685bfe.JPG").contentType("image/jpeg").size(62412L).images(Map.of("600", "external_13469017566287%2FE36ZvDh6pA%2BAjFaI81ETzw%3D%3D")))),
      messageInfoWithCustomEntities1);
    assertEquals(new MessageInfoWithCustomEntities()
        .messageId("messageId3___n3ZVrIxbQ")
        .timestamp(1665413648L)
        .message("message with attachment")
        .attachments(List.of(new AttachmentInfo().id("fileId").fileName("fileName.pdf").contentType("application/pdf").size(123L).images(Map.of("600", "fileId600")))),
      messageInfoWithCustomEntities2);
  }

  //////////////////////
  //// Send Contact ////
  //////////////////////

  @Test
  public void sendContactMessage_noFederatedAccount()  {
    assertThrows(SendMessageFailedProblem.class, () ->
      symphonyMessageSender.sendContactMessage("streamId", "123456789", "text", "{ \"type\": \"unknownType\"}", "<div data-format=\"PresentationML\" data-version=\"2.0\">PresentationML Content</div>")
    );
  }

  @Test
  public void sendContactMessage_EncryptionProblem() throws EncryptionException {
    federatedAccountRepository.save(FederatedAccount.builder().symphonyUserId("123456789").symphonyUsername("wa_bot_user_name").build());

    when(messageEncryptor.buildContactMessage("123456789", "wa_bot_user_name", "streamId", "text", "{ \"type\": \"unknownType\"}", "<div data-format=\"PresentationML\" data-version=\"2.0\">PresentationML Content</div>")).thenThrow(new EncryptionException(null));

    Optional<MessageInfoWithCustomEntities> newMessage = symphonyMessageSender.sendContactMessage("streamId", "123456789", "text", "{ \"type\": \"unknownType\"}", "<div data-format=\"PresentationML\" data-version=\"2.0\">PresentationML Content</div>");

    verify(messageMetrics, times(1)).onMessageBlockToSymphony(ENCRYPTION_FAILED, "streamId");
    assertTrue(newMessage.isEmpty());
  }

  @Test
  public void sendContactMessage_OK() throws EncryptionException, JsonProcessingException {
    federatedAccountRepository.save(FederatedAccount.builder().symphonyUserId("123456789").symphonyUsername("wa_bot_user_name").build());

    SBEEventMessage messageToBeSent = SBEEventMessage.builder().build();
    when(messageEncryptor.buildContactMessage("123456789", "wa_bot_user_name", "streamId", "text", "{ \"type\": \"send_contacts\"}", "<div data-format=\"PresentationML\" data-version=\"2.0\">PresentationML Content</div>")).thenReturn(messageToBeSent);
    when(symphonyService.sendBulkMessage(eq(messageToBeSent), any(SessionSupplier.class))).thenReturn(SBEEventMessage.builder().messageId("NxqDE3jYX/ePoCu+ytgVXH///oOG+B9FdA==").build());

    Optional<MessageInfoWithCustomEntities> newMessage = symphonyMessageSender.sendContactMessage("streamId", "123456789", "text", "{ \"type\": \"send_contacts\"}", "<div data-format=\"PresentationML\" data-version=\"2.0\">PresentationML Content</div>");

    verify(messageMetrics, times(1)).onSendMessageToSymphony("123456789", "streamId");
    assertEquals(newMessage.get().getMessageId(), "NxqDE3jYX_ePoCu-ytgVXH___oOG-B9FdA");

  }

}
