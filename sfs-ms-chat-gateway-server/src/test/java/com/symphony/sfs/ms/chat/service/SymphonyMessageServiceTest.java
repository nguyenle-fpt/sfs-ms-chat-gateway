package com.symphony.sfs.ms.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.symphony.sfs.ms.chat.config.EmpConfig;
import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.datafeed.MessageDecryptor;
import com.symphony.sfs.ms.chat.exception.DecryptionException;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import com.symphony.sfs.ms.chat.generated.model.MessageId;
import com.symphony.sfs.ms.chat.generated.model.MessageInfoWithCustomEntities;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessagesResponse;
import com.symphony.sfs.ms.chat.generated.model.SendMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.SymphonyAttachment;
import com.symphony.sfs.ms.chat.mapper.MessageInfoMapper;
import com.symphony.sfs.ms.chat.mapper.MessageInfoMapperImpl;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.sbe.MessageEncryptor;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyService;
import com.symphony.sfs.ms.chat.util.SymphonySystemMessageTemplateProcessor;
import com.symphony.sfs.ms.starter.config.JacksonConfiguration;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.config.properties.common.PemResource;
import com.symphony.sfs.ms.starter.health.MeterManager;
import com.symphony.sfs.ms.starter.security.SessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonyRsaAuthFunction;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.message.MessageStatusService;
import com.symphony.sfs.ms.starter.symphony.stream.MessageEnvelope;
import com.symphony.sfs.ms.starter.symphony.stream.SBEEventMessage;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyInboundMessage;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyOutboundAttachment;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyOutboundMessage;
import com.symphony.sfs.ms.starter.symphony.stream.ThreadMessagesResponse;
import com.symphony.sfs.ms.starter.symphony.tds.TenantDetailRepository;
import com.symphony.sfs.ms.starter.util.RsaUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import model.InboundMessage;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.OffsetDateTime;
import java.util.Arrays;
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
import static com.symphony.sfs.ms.starter.util.RsaUtils.parseRSAPrivateKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SymphonyMessageServiceTest {

  private SessionSupplier<SymphonySession> userSession;
  private AuthenticationService authenticationService;
  private PodConfiguration podConfiguration;
  private BotConfiguration botConfiguration;
  private ChatConfiguration chatConfiguration;

  private FederatedAccountRepository federatedAccountRepository;
  private StreamService streamService;
  private SymphonySystemMessageTemplateProcessor templateProcessor;

  private SymphonyMessageSender symphonyMessageSender;
  private SymphonyMessageService symphonyMessageService;
  private SymphonyService symphonyService;
  private DatafeedSessionPool datafeedSessionPool;
  private MessageEncryptor messageEncryptor;
  private MessageDecryptor messageDecryptor;

  private EmpClient empClient;
  private EmpConfig empConfig;
  private TenantDetailRepository tenantDetailRepository;
  private AdminClient adminClient;
  private EmpSchemaService empSchemaService;
  private MessageStatusService messageStatusService;
  private ObjectMapper objectMapper;


  @BeforeEach
  public void setUp() throws Exception {
    MeterManager meterManager = new MeterManager(new SimpleMeterRegistry(), Optional.empty());

    // keys
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair keyPair = kpg.generateKeyPair();

    podConfiguration = new PodConfiguration();
    podConfiguration.setUrl("podUrl");
    podConfiguration.setSessionAuth("sessionAuth");
    podConfiguration.setKeyAuth("keyAuth");

    chatConfiguration = new ChatConfiguration();
    chatConfiguration.setSharedPrivateKey(new PemResource((RsaUtils.encodeRSAKey(keyPair.getPrivate()))));

    botConfiguration = new BotConfiguration();
    botConfiguration.setPrivateKey(new PemResource(RsaUtils.encodeRSAKey(keyPair.getPrivate())));
    botConfiguration.setUsername("bot");

    authenticationService = mock(AuthenticationService.class);
    federatedAccountRepository = mock(FederatedAccountRepository.class);
    streamService = mock(StreamService.class);
    templateProcessor = mock(SymphonySystemMessageTemplateProcessor.class);

    datafeedSessionPool = mock(DatafeedSessionPool.class);
    symphonyService = mock(SymphonyService.class);

    userSession = new SessionSupplier<>("username", new SymphonyRsaAuthFunction(authenticationService, podConfiguration, parseRSAPrivateKey(chatConfiguration.getSharedPrivateKey().getData())));


    messageEncryptor = mock(MessageEncryptor.class);
    messageDecryptor = mock(MessageDecryptor.class);

    MessageInfoMapper messageInfoMapper = new MessageInfoMapperImpl();

    symphonyMessageSender = spy(new SymphonyMessageSender(podConfiguration, datafeedSessionPool, federatedAccountRepository, streamService, templateProcessor, new MessageIOMonitor(meterManager), messageEncryptor, messageDecryptor, symphonyService, null, null, messageInfoMapper, new ObjectMapper()));
    empClient = mock(EmpClient.class);
    empConfig = new EmpConfig();
    tenantDetailRepository = mock(TenantDetailRepository.class);
    adminClient = mock(AdminClient.class);
    empSchemaService = mock(EmpSchemaService.class);
    messageStatusService = mock(MessageStatusService.class);
    objectMapper = spy(new JacksonConfiguration().configureJackson(new ObjectMapper()));

    symphonyMessageService = new SymphonyMessageService(empConfig, empClient, tenantDetailRepository, federatedAccountRepository, mock(ForwarderQueueConsumer.class), datafeedSessionPool, symphonyMessageSender, adminClient, empSchemaService, symphonyService, messageStatusService, podConfiguration, botConfiguration, streamService, new MessageIOMonitor(meterManager), mock(MessageSource.class), mock(MessageDecryptor.class), objectMapper);

  }

  @Test
  void sendRawMessage() {
    FederatedAccount federatedAccount = FederatedAccount.builder()
      .symphonyUsername("username")
      .build();
    when(datafeedSessionPool.getSessionSupplier(federatedAccount)).thenReturn(userSession);
    when(federatedAccountRepository.findBySymphonyId("fromSymphonyUserId")).thenReturn(Optional.of(federatedAccount));
    when(streamService.sendMessage("podUrl", userSession, "streamId", "text")).thenReturn(Optional.of(new InboundMessage()));

    symphonyMessageSender.sendRawMessage("streamId", "fromSymphonyUserId", "text", "toSymphonyUserId");

    verify(streamService, once()).sendMessage(eq(podConfiguration.getUrl()), any(SessionSupplier.class), eq("streamId"), eq("text"));
  }

  @Test
  void sendRawMessageWithAttachment() throws UnknownDatafeedUserException {
    FederatedAccount federatedAccount = FederatedAccount.builder()
      .symphonyUsername("username")
      .build();

    when(datafeedSessionPool.getSessionSupplier(federatedAccount)).thenReturn(mock(SessionSupplier.class));

    byte[] data = new byte[] {1, 2, 3, 4, 5};
    SymphonyAttachment attachment = new SymphonyAttachment()
                                        .fileName("filename.png")
                                        .contentType("image/png")
                                        .data(Base64.encodeBase64String(data));

    when(federatedAccountRepository.findBySymphonyId("fromSymphonyUserId")).thenReturn(Optional.of(federatedAccount));

    SymphonyOutboundMessage symphonyOutboundMessage = SymphonyOutboundMessage.builder().message("text")
      .attachment(new SymphonyOutboundAttachment[] { SymphonyOutboundAttachment.builder().mediaType(MediaType.IMAGE_PNG).name("filename.png").data(data).build()}).build();

    when(streamService.sendMessageMultiPart(eq("podUrl"), any(), eq("streamId"), eq(symphonyOutboundMessage), eq(false))).thenReturn(Optional.of(new SymphonyInboundMessage()));
    symphonyMessageSender.sendRawMessageWithAttachments("streamId", "fromSymphonyUserId", "text", "toSymphonyUserId", Collections.singletonList(attachment));

    verify(streamService, once()).sendMessageMultiPart(eq(podConfiguration.getUrl()), any(SessionSupplier.class), eq("streamId"), eq(symphonyOutboundMessage), eq(false));
  }

  @TestInstance(PER_CLASS)
  @Nested
  class SystemMessagesTest {
    @ParameterizedTest
    @MethodSource("templateProvider")
    public void sendSystemMessage(String templateName, String detemplatizedMessage, TriConsumer<SessionSupplier<SymphonySession>, String, String> messageSender) {

      when(templateProcessor.process("templatizedText", templateName)).thenReturn(detemplatizedMessage);
      when(streamService.sendMessage("podUrl", userSession, "streamId", "simpleDetemplatizedText")).thenReturn(Optional.of(new InboundMessage()));
      when(streamService.sendMessage("podUrl", userSession, "streamId", "infoDetemplatizedText")).thenReturn(Optional.of(new InboundMessage()));
      when(streamService.sendMessage("podUrl", userSession, "streamId", "notificationDetemplatizedText")).thenReturn(Optional.of(new InboundMessage()));

      messageSender.accept(userSession, "streamId", "templatizedText");

      InOrder orderVerifier = inOrder(symphonyMessageSender, templateProcessor);
      orderVerifier.verify(templateProcessor, once()).process("templatizedText", templateName);
      orderVerifier.verify(symphonyMessageSender, once()).sendRawMessage(userSession, "streamId", detemplatizedMessage);
    }

    private Stream<Arguments> templateProvider() {
      return Stream.of(
        // arguments(<templateName>, <detemplatizedMessage>, <Method to send message as a TriConsumer>)
        arguments(SYSTEM_MESSAGE_SIMPLE_HANDLEBARS_TEMPLATE, "simpleDetemplatizedText", (TriConsumer<SessionSupplier<SymphonySession>, String, String>) (session, streamId, text) -> symphonyMessageSender.sendSimpleMessage(session, streamId, text)),
        arguments(SYSTEM_MESSAGE_INFORMATION_HANDLEBARS_TEMPLATE, "infoDetemplatizedText", (TriConsumer<SessionSupplier<SymphonySession>, String, String>) (session, streamId, text) -> symphonyMessageSender.sendInfoMessage(session, streamId, text)),
        arguments(SYSTEM_MESSAGE_NOTIFICATION_HANDLEBARS_TEMPLATE, "notificationDetemplatizedText", (TriConsumer<SessionSupplier<SymphonySession>, String, String>) (session, streamId, text) -> symphonyMessageSender.sendNotificationMessage(session, streamId, text))
      );
    }

  }

  @Test
  void sendAlertMessage() {
    when(templateProcessor.process("templatizedText", "title", Collections.emptyList(), SYSTEM_MESSAGE_ALERT_HANDLEBARS_TEMPLATE)).thenReturn("alertDetemplatizedText");
    when(streamService.sendMessage("podUrl", userSession, "streamId", "alertDetemplatizedText")).thenReturn(Optional.of(new InboundMessage()));
    when(streamService.sendMessage("podUrl", userSession, "streamId", null)).thenReturn(Optional.of(new InboundMessage()));

    symphonyMessageSender.sendAlertMessage(userSession, "streamId", "templatizedText", "title", Collections.emptyList());

    InOrder orderVerifier = inOrder(symphonyMessageSender, templateProcessor);
    orderVerifier.verify(templateProcessor, once()).process("templatizedText", "title", Collections.emptyList(), SYSTEM_MESSAGE_ALERT_HANDLEBARS_TEMPLATE);
    orderVerifier.verify(symphonyMessageSender, once()).sendRawMessage(userSession, "streamId", "alertDetemplatizedText");


    symphonyMessageSender.sendAlertMessage(userSession, "streamId", "templatizedText", Collections.emptyList());
    orderVerifier.verify(templateProcessor, once()).process("templatizedText", null, Collections.emptyList(), SYSTEM_MESSAGE_ALERT_HANDLEBARS_TEMPLATE);
  }

  @Test
  void sendRawMessage_FromSymphonyUserNotFound() {
    when(federatedAccountRepository.findBySymphonyId("fromSymphonyUserId")).thenReturn(Optional.empty());
    assertThrows(SendMessageFailedProblem.class, () -> symphonyMessageSender.sendRawMessage("streamId", "fromSymphonyUserId", "text", "toSymphonyUserId"));
  }

  // TODO symphonyMessageService is a mock
  @Test
  public void retrieveMessagesTest() throws UnknownDatafeedUserException, DecryptionException {
    MessageId messageId = new MessageId().messageId("messageId1___n1ZVrIxbQ");
    List<MessageId> messagesIds = Collections.singletonList(messageId);
    String threadId = "threadId";
    String fromSymphonyUserId = "fromSymphonyUserId";
    OffsetDateTime start = OffsetDateTime.now().minusHours(1);
    OffsetDateTime end = OffsetDateTime.now();

    MessageInfoWithCustomEntities messageInfoWithCustomEntities = new MessageInfoWithCustomEntities();
    messageInfoWithCustomEntities.setMessage("message");
    messageInfoWithCustomEntities.setMessageId("messageId1___n1ZVrIxbQ");

    MessageEnvelope messageEnvelope = MessageEnvelope.builder().message(SBEEventMessage.builder().messageId("messageId1///n1ZVrIxbQ==").build()).build();
    MessageEnvelope messageEnvelope2 = MessageEnvelope.builder().message(SBEEventMessage.builder().messageId("messageId2///n2ZVrIxbQ==").build()).build();
    List<MessageEnvelope> messageEnvelopes = Arrays.asList(messageEnvelope, messageEnvelope2);
    ThreadMessagesResponse threadMessagesResponse = ThreadMessagesResponse.builder().envelopes(messageEnvelopes).build();

    when(datafeedSessionPool.getSessionSupplierOrFail(fromSymphonyUserId)).thenReturn(userSession);
    when(streamService.retrieveSocialMessagesList(podConfiguration.getUrl(), userSession, threadId, SymphonyMessageService.POD_BATCH_REQUEST_SIZE, start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli())).thenReturn(Optional.of(threadMessagesResponse));
    doReturn(messageInfoWithCustomEntities).when(symphonyMessageSender).decryptAndBuildMessageInfo(any(SBEEventMessage.class), eq(fromSymphonyUserId), eq(userSession), any());

    RetrieveMessagesResponse response = symphonyMessageService.retrieveMessages(threadId, messagesIds, fromSymphonyUserId, start, end);
    assertEquals(1, response.getMessages().size());
    assertEquals("message", response.getMessages().get(0).getMessage());
    assertEquals("messageId1___n1ZVrIxbQ", response.getMessages().get(0).getMessageId());
  }

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
