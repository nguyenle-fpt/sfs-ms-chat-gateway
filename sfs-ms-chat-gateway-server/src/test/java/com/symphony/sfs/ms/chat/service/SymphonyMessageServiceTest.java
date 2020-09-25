package com.symphony.sfs.ms.chat.service;

import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import com.symphony.sfs.ms.chat.generated.model.MessageId;
import com.symphony.sfs.ms.chat.generated.model.MessageInfo;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessagesResponse;
import com.symphony.sfs.ms.chat.generated.model.SendMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.SymphonyAttachment;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyService;
import com.symphony.sfs.ms.chat.util.SymphonySystemMessageTemplateProcessor;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.config.properties.common.PemResource;
import com.symphony.sfs.ms.starter.health.MeterManager;
import com.symphony.sfs.ms.starter.security.StaticSessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyOutboundAttachment;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyOutboundMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.springframework.http.MediaType;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static com.symphony.sfs.ms.chat.service.SymphonyMessageSender.SYSTEM_MESSAGE_ALERT_HANDLEBARS_TEMPLATE;
import static com.symphony.sfs.ms.chat.service.SymphonyMessageSender.SYSTEM_MESSAGE_INFORMATION_HANDLEBARS_TEMPLATE;
import static com.symphony.sfs.ms.chat.service.SymphonyMessageSender.SYSTEM_MESSAGE_NOTIFICATION_HANDLEBARS_TEMPLATE;
import static com.symphony.sfs.ms.chat.service.SymphonyMessageSender.SYSTEM_MESSAGE_SIMPLE_HANDLEBARS_TEMPLATE;
import static com.symphony.sfs.ms.starter.testing.MockitoUtils.once;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SymphonyMessageServiceTest {

  private SymphonySession userSession;
  private AuthenticationService authenticationService;
  private PodConfiguration podConfiguration;
  private ChatConfiguration chatConfiguration;

  private FederatedAccountRepository federatedAccountRepository;
  private StreamService streamService;
  private SymphonySystemMessageTemplateProcessor templateProcessor;

  private SymphonyMessageSender symphonyMessageSender;
  private SymphonyMessageService symphonyMessageService;
  private SymphonyService symphonyService;
  private DatafeedSessionPool datafeedSessionPool;

  @BeforeEach
  public void setUp() {
    MeterManager meterManager = new MeterManager(new SimpleMeterRegistry(), Optional.empty());

    podConfiguration = new PodConfiguration();
    podConfiguration.setUrl("podUrl");
    podConfiguration.setSessionAuth("sessionAuth");
    podConfiguration.setKeyAuth("keyAuth");

    chatConfiguration = new ChatConfiguration();
    chatConfiguration.setSharedPrivateKey(new PemResource("-----sharedPrivateKey"));

    authenticationService = mock(AuthenticationService.class);
    federatedAccountRepository = mock(FederatedAccountRepository.class);
    streamService = mock(StreamService.class);
    templateProcessor = mock(SymphonySystemMessageTemplateProcessor.class);

    symphonyMessageSender = mock(SymphonyMessageSender.class);
    symphonyMessageService = mock(SymphonyMessageService.class); // TODO this can't be a mock
    datafeedSessionPool = mock(DatafeedSessionPool.class);
    symphonyService = mock(SymphonyService.class);

    userSession = new SymphonySession("username", "kmToken", "sessionToken");
    when(authenticationService.authenticate(anyString(), anyString(), anyString(), anyString())).thenReturn(userSession);

    symphonyMessageSender = spy(new SymphonyMessageSender(podConfiguration, chatConfiguration, authenticationService, federatedAccountRepository, streamService, templateProcessor, new MessageIOMonitor(meterManager)));
  }

  @Test
  void sendRawMessage() {
    FederatedAccount federatedAccount = FederatedAccount.builder()
      .symphonyUsername("username")
      .build();
    when(federatedAccountRepository.findBySymphonyId("fromSymphonyUserId")).thenReturn(Optional.of(federatedAccount));

    symphonyMessageSender.sendRawMessage("streamId", "fromSymphonyUserId", "text", "toSymphonyUserId");

    verify(streamService, once()).sendMessage(eq(podConfiguration.getUrl()), any(StaticSessionSupplier.class), eq("streamId"), eq("text"));
  }

  @Test
  void sendRawMessageWithAttachment() {
    FederatedAccount federatedAccount = FederatedAccount.builder()
      .symphonyUsername("username")
      .build();

    byte[] data = new byte[] {1, 2, 3, 4, 5};
    SymphonyAttachment attachment = new SymphonyAttachment()
                                        .fileName("filename.png")
                                        .contentType("image/png")
                                        .data(Base64.encodeBase64String(data));

    when(federatedAccountRepository.findBySymphonyId("fromSymphonyUserId")).thenReturn(Optional.of(federatedAccount));

    symphonyMessageSender.sendRawMessageWithAttachments("streamId", "fromSymphonyUserId", "text", "toSymphonyUserId", Collections.singletonList(attachment));
    SymphonyOutboundMessage symphonyOutboundMessage = SymphonyOutboundMessage.builder().message("text")
      .attachment(new SymphonyOutboundAttachment[] { SymphonyOutboundAttachment.builder().mediaType(MediaType.IMAGE_PNG).name("filename.png").data(data).build()}).build();

    verify(streamService, once()).sendMessageMultiPart(eq(podConfiguration.getUrl()), any(StaticSessionSupplier.class), eq("streamId"), eq(symphonyOutboundMessage), eq(false));
  }

  @TestInstance(PER_CLASS)
  @Nested
  class SystemMessagesTest {
    @ParameterizedTest
    @MethodSource("templateProvider")
    public void sendSystemMessage(String templateName, String detemplatizedMessage, TriConsumer<SymphonySession, String, String> messageSender) {

      when(templateProcessor.process("templatizedText", templateName)).thenReturn(detemplatizedMessage);

      messageSender.accept(userSession, "streamId", "templatizedText");

      InOrder orderVerifier = inOrder(symphonyMessageSender, templateProcessor);
      orderVerifier.verify(templateProcessor, once()).process("templatizedText", templateName);
      orderVerifier.verify(symphonyMessageSender, once()).sendRawMessage(userSession, "streamId", detemplatizedMessage);
    }

    private Stream<Arguments> templateProvider() {
      return Stream.of(
        // arguments(<templateName>, <detemplatizedMessage>, <Method to send message as a TriConsumer>)
        arguments(SYSTEM_MESSAGE_SIMPLE_HANDLEBARS_TEMPLATE, "simpleDetemplatizedText", (TriConsumer<SymphonySession, String, String>) (session, streamId, text) -> symphonyMessageSender.sendSimpleMessage(session, streamId, text)),
        arguments(SYSTEM_MESSAGE_INFORMATION_HANDLEBARS_TEMPLATE, "infoDetemplatizedText", (TriConsumer<SymphonySession, String, String>) (session, streamId, text) -> symphonyMessageSender.sendInfoMessage(session, streamId, text)),
        arguments(SYSTEM_MESSAGE_ALERT_HANDLEBARS_TEMPLATE, "alertDetemplatizedText", (TriConsumer<SymphonySession, String, String>) (session, streamId, text) -> symphonyMessageSender.sendAlertMessage(session, streamId, text)),
        arguments(SYSTEM_MESSAGE_NOTIFICATION_HANDLEBARS_TEMPLATE, "notificationDetemplatizedText", (TriConsumer<SymphonySession, String, String>) (session, streamId, text) -> symphonyMessageSender.sendNotificationMessage(session, streamId, text))
      );
    }

  }


  @Test
  void sendRawMessage_FromSymphonyUserNotFound() {
    when(federatedAccountRepository.findBySymphonyId("fromSymphonyUserId")).thenReturn(Optional.empty());
    assertThrows(SendMessageFailedProblem.class, () -> symphonyMessageSender.sendRawMessage("streamId", "fromSymphonyUserId", "text", "toSymphonyUserId"));
  }

  // TODO symphonyMessageService is a mock
//  @Test
//  public void retrieveMessagesTest() throws UnknownDatafeedUserException {
//    MessageId messageId = new MessageId().messageId("messageId");
//
//    List<MessageId> messagesIds = Collections.singletonList(messageId);
//    String fromSymphonyUserId = "fromSymphonyUserId";
//    SymphonySession userSession = new SymphonySession("username", "kmToken", "sessionToken");
//    DatafeedSessionPool.DatafeedSession session = new DatafeedSessionPool.DatafeedSession(userSession, "fromSymphonyUserId");
//    MessageInfo messageInfo = new MessageInfo().message("message").messageId("messageId");
//
//    when(datafeedSessionPool.refreshSession("fromSymphonyUserId")).thenReturn(session);
//    when(symphonyService.getMessage("messageId", session, "podUrl")).thenReturn(Optional.of(messageInfo));
//
//    RetrieveMessagesResponse response = symphonyMessageService.retrieveMessages(messagesIds, fromSymphonyUserId);
//    assertEquals(1, response.getMessages().size());
//    assertEquals("message", response.getMessages().get(0).getMessage());
//    assertEquals("messageId", response.getMessages().get(0).getMessageId());
//  }

  @FunctionalInterface
  private interface TriConsumer<T, U, V> {
    /**
     * Performs this operation on the given arguments.
     *
     * @param t the first input argument
     * @param u the second input argument
     * @param v then third argument
     */
    void accept(T t, U u, V v);

    /**
     * Returns a composed {@code TriConsumer} that performs, in sequence, this
     * operation followed by the {@code after} operation. If performing either
     * operation throws an exception, it is relayed to the caller of the
     * composed operation.  If performing this operation throws an exception,
     * the {@code after} operation will not be performed.
     *
     * @param after the operation to perform after this operation
     * @return a composed {@code TriConsumer} that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws NullPointerException if {@code after} is null
     */
    default TriConsumer<T, U, V> andThen(TriConsumer<? super T, ? super U, ? super V> after) {
      Objects.requireNonNull(after);

      return (t, u, v) -> {
        accept(t, u, v);
        after.accept(t, u, v);
      };
    }
  }
}
