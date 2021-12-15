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
import com.symphony.sfs.ms.starter.symphony.auth.SymphonyRsaAuthFunction;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.context.MessageSource;

import java.util.Optional;

import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseToSymphony.ENCRYPTION_FAILED;
import static com.symphony.sfs.ms.starter.util.RsaUtils.parseRSAPrivateKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
  private SessionSupplier<SymphonySession> symphonySessionSupplier;

  @BeforeEach
  public void setUp(MessageSource messageSource) throws Exception {
    templateProcessor = mock(SymphonySystemMessageTemplateProcessor.class);
    messageMetrics = mock(MessageIOMonitor.class);
    messageEncryptor = mock(MessageEncryptor.class);
    messageDecryptor = mock(MessageDecryptor.class);
    symphonyService = mock(SymphonyService.class);
    symphonyMessageSender = new SymphonyMessageSender(podConfiguration, datafeedSessionPool, federatedAccountRepository, streamService, templateProcessor, messageMetrics, messageEncryptor, messageDecryptor, symphonyService);
    symphonySessionSupplier = new SessionSupplier<>("wa_bot_user_name", new SymphonyRsaAuthFunction(authenticationService, podConfiguration, parseRSAPrivateKey(chatConfiguration.getSharedPrivateKey().getData())));

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

    SBEEventMessage parentMessage = SBEEventMessage.builder().build();
    when(symphonyService.getEncryptedMessage("message_id", symphonySessionSupplier)).thenReturn(Optional.of(parentMessage));
    when(messageEncryptor.encrypt("123456789", "streamId", "message text", parentMessage)).thenThrow(new EncryptionException(null));

    Optional<String> newMessageId = symphonyMessageSender.sendReplyMessage("streamId", "123456789", "message text", "message_id");

    verify(messageMetrics, times(1)).onMessageBlockToSymphony(ENCRYPTION_FAILED, "streamId");
    assertTrue(newMessageId.isEmpty());
  }

  @Test
  public void sendReplyMessageSuccesfully() throws EncryptionException, JsonProcessingException {
    federatedAccountRepository.save(FederatedAccount.builder().symphonyUserId("123456789").symphonyUsername("wa_bot_user_name").build());

    SBEEventMessage parentMessage = SBEEventMessage.builder().build();
    when(symphonyService.getEncryptedMessage("message_id", symphonySessionSupplier)).thenReturn(Optional.of(parentMessage));
    SBEEventMessage messageToBeSent = SBEEventMessage.builder().build();
    when(messageEncryptor.encrypt("123456789", "streamId", "message text", parentMessage)).thenReturn(messageToBeSent);
    when(symphonyService.sendReplyMessage(messageToBeSent, symphonySessionSupplier)).thenReturn(SBEEventMessage.builder().messageId("NxqDE3jYX/ePoCu+ytgVXH///oOG+B9FdA==").build());

    Optional<String> newMessageId = symphonyMessageSender.sendReplyMessage("streamId", "123456789", "message text", "message_id");

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
    when(messageEncryptor.encrypt("123456789", "streamId", "message text", parentMessage)).thenReturn(messageToBeSent);
    when(symphonyService.sendReplyMessage(messageToBeSent, symphonySessionSupplier)).thenReturn(SBEEventMessage.builder().messageId("NxqDE3jYX/ePoCu+ytgVXH///oOG+B9FdA==").build());

    Optional<String> newMessageId = symphonyMessageSender.sendReplyMessage("streamId", "123456789", "message text", "message_id");

    verify(symphonyService, times(2)).getEncryptedMessage(any(String.class), any(SessionSupplier.class));
    assertEquals(newMessageId.get(), "NxqDE3jYX_ePoCu-ytgVXH___oOG-B9FdA");

  }
}
