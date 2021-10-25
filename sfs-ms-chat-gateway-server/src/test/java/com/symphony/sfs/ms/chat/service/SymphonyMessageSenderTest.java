package com.symphony.sfs.ms.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.symphony.sfs.ms.chat.api.util.AbstractIntegrationTest;
import com.symphony.sfs.ms.chat.datafeed.MessageDecryptor;
import com.symphony.sfs.ms.chat.datafeed.SBEEventMessage;
import com.symphony.sfs.ms.chat.exception.EncryptionException;
import com.symphony.sfs.ms.chat.generated.model.SendMessageFailedProblem;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.sbe.MessageEncryptor;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyService;
import com.symphony.sfs.ms.chat.util.SymphonySystemMessageTemplateProcessor;
import com.symphony.sfs.ms.starter.security.SessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonyAuthFactory;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.context.MessageSource;

import java.util.Optional;

import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseToSymphony.ENCRYPTION_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
  private SymphonyAuthFactory symphonyAuthFactory;

  @BeforeEach
  public void setUp(MessageSource messageSource) throws Exception {
    templateProcessor = mock(SymphonySystemMessageTemplateProcessor.class);
    messageMetrics = mock(MessageIOMonitor.class);
    messageEncryptor = mock(MessageEncryptor.class);
    messageDecryptor = mock(MessageDecryptor.class);
    symphonyService = mock(SymphonyService.class);
    symphonyAuthFactory = mock(SymphonyAuthFactory.class);
    symphonyMessageSender = new SymphonyMessageSender(podConfiguration, chatConfiguration, authenticationService, federatedAccountRepository, streamService, templateProcessor, messageMetrics, messageEncryptor, messageDecryptor, symphonyService, symphonyAuthFactory);
  }

  @Test
  public void sendReply_FederatedUserNotFound() {
    assertThrows(SendMessageFailedProblem.class, () -> {
      symphonyMessageSender.sendReplyMessage("streamId", "123456789", "message text", "message_id");
    });
  }

  @Test
  public void sendReply_DecryptionEncryptionProblem() throws EncryptionException {
    federatedAccountRepository.save(FederatedAccount.builder().symphonyUserId("123456789").symphonyUsername("wa_bot_user_name").build());
    SymphonySession session = new SymphonySession();
    when(authenticationService.authenticate(anyString(), anyString(), anyString(), anyString())).thenReturn(session);
    SBEEventMessage parentMessage = SBEEventMessage.builder().build();
    when(symphonyService.getEncryptedMessage("message_id", session)).thenReturn(Optional.of(parentMessage));
    when(messageEncryptor.encrypt("123456789", "streamId", "message text", parentMessage)).thenThrow(new EncryptionException(null));

    Optional<String> newMessageId = symphonyMessageSender.sendReplyMessage("streamId", "123456789", "message text", "message_id");

    verify(messageMetrics, times(1)).onMessageBlockToSymphony(ENCRYPTION_FAILED, "streamId");
    assertTrue(newMessageId.isEmpty());
  }

  @Test
  public void sendReplyMessageSuccesfully() throws EncryptionException, JsonProcessingException {
    federatedAccountRepository.save(FederatedAccount.builder().symphonyUserId("123456789").symphonyUsername("wa_bot_user_name").build());
    SymphonySession session = new SymphonySession();
    when(authenticationService.authenticate(anyString(), anyString(), anyString(), anyString())).thenReturn(session);
    SBEEventMessage parentMessage = SBEEventMessage.builder().build();
    when(symphonyService.getEncryptedMessage("message_id", session)).thenReturn(Optional.of(parentMessage));
    SBEEventMessage messageToBeSent = SBEEventMessage.builder().build();
    when(messageEncryptor.encrypt("123456789", "streamId", "message text", parentMessage)).thenReturn(messageToBeSent);
    when(symphonyService.sendReplyMessage(messageToBeSent, session)).thenReturn(SBEEventMessage.builder().messageId("NxqDE3jYX/ePoCu+ytgVXH///oOG+B9FdA==").build());

    Optional<String> newMessageId = symphonyMessageSender.sendReplyMessage("streamId", "123456789", "message text", "message_id");

    verify(messageMetrics, times(1)).onSendMessageToSymphony("123456789", "streamId");
    assertEquals(newMessageId.get(), "NxqDE3jYX_ePoCu-ytgVXH___oOG-B9FdA");

  }

  @Test
  public void sendReplyToHistoricMessage() throws EncryptionException, JsonProcessingException {
    federatedAccountRepository.save(FederatedAccount.builder().symphonyUserId("123456789").symphonyUsername("wa_bot_user_name").build());
    SymphonySession waUserSession = new SymphonySession();
    SymphonySession waBotSession = new SymphonySession();
    when(authenticationService.authenticate(anyString(), anyString(), anyString(), anyString())).thenReturn(waUserSession);
    when(symphonyAuthFactory.getBotAuth()).thenReturn(new SessionSupplier() {
      @Override
      public SymphonySession get() {
        return waBotSession;
      }
    });
    SBEEventMessage parentMessage = SBEEventMessage.builder().build();
    when(symphonyService.getEncryptedMessage("message_id", waUserSession)).thenAnswer((Answer<Optional<SBEEventMessage>>) invocation -> {
        SymphonySession session = invocation.getArgument(1);
        if (session == waUserSession) {
          throw new RuntimeException();
        } else {
          return Optional.of(parentMessage);
        }
    });
    SBEEventMessage messageToBeSent = SBEEventMessage.builder().build();
    when(messageEncryptor.encrypt("123456789", "streamId", "message text", parentMessage)).thenReturn(messageToBeSent);
    when(symphonyService.sendReplyMessage(messageToBeSent, waUserSession)).thenReturn(SBEEventMessage.builder().messageId("NxqDE3jYX/ePoCu+ytgVXH///oOG+B9FdA==").build());

    Optional<String> newMessageId = symphonyMessageSender.sendReplyMessage("streamId", "123456789", "message text", "message_id");

    verify(symphonyService, times(2)).getEncryptedMessage(any(String.class), any(SymphonySession.class));
    assertEquals(newMessageId.get(), "NxqDE3jYX_ePoCu-ytgVXH___oOG-B9FdA");

  }
}
