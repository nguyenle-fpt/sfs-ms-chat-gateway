package com.symphony.sfs.ms.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.symphony.sfs.ms.chat.api.util.AbstractIntegrationTest;
import com.symphony.sfs.ms.chat.datafeed.MessageDecryptor;
import com.symphony.sfs.ms.chat.exception.DecryptionException;
import com.symphony.sfs.ms.chat.exception.EncryptionException;
import com.symphony.sfs.ms.chat.exception.InlineReplyMessageException;
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
    symphonyMessageSender = new SymphonyMessageSender(podConfiguration, datafeedSessionPool, federatedAccountRepository, streamService, templateProcessor, messageMetrics, messageEncryptor, messageDecryptor, symphonyService, empSchemaService, messageSource, messageInfoMapper);
    userSession = new SessionSupplier<>("username", new SymphonyRsaAuthFunction(authenticationService, podConfiguration, parseRSAPrivateKey(chatConfiguration.getSharedPrivateKey().getData())));
  }

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
  public void decryptAndBuildMessageInfo_WithQuote() throws EncryptionException, DecryptionException {
    String symphonyUserId = "123456789";
    String messageId = "messageId1///n1ZVrIxbQ==";

    Map<String, Object> data = new HashMap<>();
    data.put("id", messageId);
    CustomEntity customEntity = CustomEntity.builder().data(data).type(CustomEntity.QUOTE_TYPE).endIndex(0).build();
    List<CustomEntity> parsedCustomEntities = List.of(customEntity);

    SBEEventMessage sbeEventMessage1 = SBEEventMessage.builder().messageId(messageId).text("Message").build();
    SBEEventMessage sbeEventMessage2 = SBEEventMessage.builder().messageId("messageId2///n2ZVrIxbQ==").text("Reply to message").parsedCustomEntities(parsedCustomEntities).build();
    Map<String, SBEEventMessage> retrievedMessage = new HashMap<>();
    retrievedMessage.put("messageId1___n1ZVrIxbQ", sbeEventMessage1);
    retrievedMessage.put("messageId2___n2ZVrIxbQ", sbeEventMessage2);

    doNothing().when(messageDecryptor).decrypt(any(SBEEventMessage.class), eq(symphonyUserId), eq(userSession.getPrincipal()));

    MessageInfoWithCustomEntities messageInfoWithCustomEntities = symphonyMessageSender.decryptAndBuildMessageInfo(sbeEventMessage2, symphonyUserId, userSession, retrievedMessage);
    assertEquals("messageId2___n2ZVrIxbQ", messageInfoWithCustomEntities.getMessageId());
    assertNotNull(messageInfoWithCustomEntities.getParentMessage());
  }

}
