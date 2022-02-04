package com.symphony.sfs.ms.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.symphony.sfs.ms.chat.api.util.AbstractIntegrationTest;
import com.symphony.sfs.ms.chat.datafeed.MessageDecryptor;
import com.symphony.sfs.ms.chat.datafeed.SBEEventMessage;
import com.symphony.sfs.ms.chat.datafeed.SBEMessageAttachment;
import com.symphony.sfs.ms.chat.exception.EncryptionException;
import com.symphony.sfs.ms.chat.exception.InlineReplyMessageException;
import com.symphony.sfs.ms.chat.generated.model.SendMessageFailedProblem;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.sbe.MessageEncryptor;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyService;
import com.symphony.sfs.ms.chat.util.SymphonySystemMessageTemplateProcessor;
import com.symphony.sfs.ms.starter.security.SessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.context.MessageSource;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseToSymphony.ENCRYPTION_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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

  @BeforeEach
  public void setUp(MessageSource messageSource) throws Exception {
    templateProcessor = mock(SymphonySystemMessageTemplateProcessor.class);
    messageMetrics = mock(MessageIOMonitor.class);
    messageEncryptor = mock(MessageEncryptor.class);
    messageDecryptor = mock(MessageDecryptor.class);
    symphonyService = mock(SymphonyService.class);
    symphonyMessageSender = new SymphonyMessageSender(podConfiguration, datafeedSessionPool, federatedAccountRepository, streamService, templateProcessor, messageMetrics, messageEncryptor, messageDecryptor, symphonyService, empSchemaService, messageSource);
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
    Optional<String> newMessageId = symphonyMessageSender.sendReplyMessage("streamId", "123456789", "message text", "message_id", false, Optional.empty());

    verify(messageMetrics, times(1)).onMessageBlockToSymphony(ENCRYPTION_FAILED, "streamId");
    assertTrue(newMessageId.isEmpty());
  }

  @Test
  public void sendReplyMessageSuccesfully_AttachmentNotSupported() throws EncryptionException, JsonProcessingException {
    federatedAccountRepository.save(FederatedAccount.builder().symphonyUserId("123456789").symphonyUsername("wa_bot_user_name").build());

    SBEEventMessage parentMessage = SBEEventMessage.builder()
      .messageId("message_id")
      .attachments(Collections.singletonList(SBEMessageAttachment.builder().fileId("attachment_id").build()))
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

    Optional<String> newMessageId = symphonyMessageSender.sendReplyMessage("streamId", "123456789", "message text", "message_id", false, Optional.empty());

    verify(messageMetrics, times(1)).onSendMessageToSymphony("123456789", "streamId");
    assertEquals(newMessageId.get(), "NxqDE3jYX_ePoCu-ytgVXH___oOG-B9FdA");

  }

  @Test
  public void sendReplyMessageSuccesfully_Attachments() throws EncryptionException, JsonProcessingException {
    federatedAccountRepository.save(FederatedAccount.builder().symphonyUserId("123456789").symphonyUsername("wa_bot_user_name").build());

    SBEMessageAttachment parentMessageAttachment = SBEMessageAttachment.builder().fileId("attachment_1").build();
    SBEEventMessage parentMessage = SBEEventMessage.builder()
      .messageId("message_id")
      .attachments(Collections.singletonList(parentMessageAttachment))
      .build();

    SBEMessageAttachment addedAttachment = SBEMessageAttachment.builder().fileId("attachment_2").build();
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

    Optional<String> newMessageId = symphonyMessageSender.sendReplyMessage("streamId", "123456789", "message text", "message_id", true,
      Optional.of(Collections.singletonList("attachment_message_id")));

    verify(symphonyService, times(1)).getEncryptedMessage(eq("message_id"), any(SessionSupplier.class));
    verify(symphonyService, times(1)).getEncryptedMessage(eq("attachment_message_id"), any(SessionSupplier.class));
    verify(messageEncryptor, times(1)).buildReplyMessage("123456789", "wa_bot_user_name", "streamId", "message text", parentMessage, List.of(parentMessageAttachment, addedAttachment));
    verify(messageMetrics, times(1)).onSendMessageToSymphony("123456789", "streamId");
    assertEquals(newMessageId.get(), "NxqDE3jYX_ePoCu-ytgVXH___oOG-B9FdA");

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

    Optional<String> newMessageId = symphonyMessageSender.sendReplyMessage("streamId", "123456789", "message text", "message_id", false, Optional.empty());

    verify(symphonyService, times(2)).getEncryptedMessage(any(String.class), any(SessionSupplier.class));
    assertEquals(newMessageId.get(), "NxqDE3jYX_ePoCu-ytgVXH___oOG-B9FdA");

  }

  @Test
  public void sendForwardedMessage_works() throws EncryptionException, JsonProcessingException {
    federatedAccountRepository.save(FederatedAccount.builder().symphonyUserId("123456789").symphonyUsername("wa_bot_user_name").emp("WHATSAPP").build());
    SBEEventMessage messageToBeSent = SBEEventMessage.builder().build();
    when(messageEncryptor.buildForwardedMessage("123456789", "wa_bot_user_name","streamId", "message text", "from WHATSAPP\n")).thenReturn(messageToBeSent);
    when(symphonyService.sendBulkMessage(eq(messageToBeSent), any(SessionSupplier.class))).thenReturn(SBEEventMessage.builder().messageId("NxqDE3jYX/ePoCu+ytgVXH///oOG+B9FdA==").build());

    Optional<String> newMessageId = symphonyMessageSender.sendForwardedMessage("streamId", "123456789", "message text");

    verify(messageMetrics, times(1)).onSendMessageToSymphony("123456789", "streamId");
    assertEquals(newMessageId.get(), "NxqDE3jYX_ePoCu-ytgVXH___oOG-B9FdA");

  }

  @Test
  public void sendForwardedMessage_noFederatedAccount() throws EncryptionException, JsonProcessingException {
    assertThrows(SendMessageFailedProblem.class, () -> {
      symphonyMessageSender.sendForwardedMessage("streamId", "123456789", "message text");
    });
  }

  @Test
  public void sendForward_DecryptionEncryptionProblem() throws EncryptionException {
    federatedAccountRepository.save(FederatedAccount.builder().symphonyUserId("123456789").symphonyUsername("wa_bot_user_name").emp("WHATSAPP").build());

    when(messageEncryptor.buildForwardedMessage("123456789", "wa_bot_user_name", "streamId", "message text", "from WHATSAPP\n")).thenThrow(new EncryptionException(null));

    Optional<String> newMessageId = symphonyMessageSender.sendForwardedMessage("streamId", "123456789", "message text");

    verify(messageMetrics, times(1)).onMessageBlockToSymphony(ENCRYPTION_FAILED, "streamId");
    assertTrue(newMessageId.isEmpty());
  }
}
