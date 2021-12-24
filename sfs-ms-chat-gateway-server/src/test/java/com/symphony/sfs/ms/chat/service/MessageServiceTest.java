package com.symphony.sfs.ms.chat.service;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import com.symphony.oss.models.chat.canon.AttachmentEntity;
import com.symphony.oss.models.chat.canon.IAttachment;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.oss.models.core.canon.facade.PodAndUserId;
import com.symphony.sfs.ms.admin.generated.model.CanChatResponse;
import com.symphony.sfs.ms.admin.generated.model.EmpList;
import com.symphony.sfs.ms.chat.config.EmpConfig;
import com.symphony.sfs.ms.chat.datafeed.CustomEntity;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.datafeed.GatewaySocialMessage;
import com.symphony.sfs.ms.chat.datafeed.MessageDecryptor;
import com.symphony.sfs.ms.chat.datafeed.SBEEventMessage;
import com.symphony.sfs.ms.chat.datafeed.SBEEventUser;
import com.symphony.sfs.ms.chat.datafeed.SBEMessageAttachment;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.sbe.MessageEncryptor;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyService;
import com.symphony.sfs.ms.chat.util.SymphonySystemMessageTemplateProcessor;
import com.symphony.sfs.ms.emp.generated.model.AttachmentInfo;
import com.symphony.sfs.ms.emp.generated.model.ChannelMember;
import com.symphony.sfs.ms.emp.generated.model.EmpError;
import com.symphony.sfs.ms.emp.generated.model.OperationIdBySymId;
import com.symphony.sfs.ms.emp.generated.model.SendMessageResponse;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageRequest.TypeEnum;
import com.symphony.sfs.ms.emp.generated.model.SendmessagerequestInlineMessage;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.config.properties.common.PemResource;
import com.symphony.sfs.ms.starter.health.MeterManager;
import com.symphony.sfs.ms.starter.security.SessionSupplier;
import com.symphony.sfs.ms.starter.security.StaticSessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonyAuthFactory;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.message.MessageStatusService;
import com.symphony.sfs.ms.starter.symphony.stream.StreamAttributes;
import com.symphony.sfs.ms.starter.symphony.stream.StreamInfo;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.symphony.stream.StreamType;
import com.symphony.sfs.ms.starter.symphony.stream.StreamTypes;
import com.symphony.sfs.ms.starter.symphony.user.UsersInfoService;
import com.symphony.sfs.ms.starter.testing.I18nTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import model.UserInfo;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.springframework.context.MessageSource;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.symphony.sfs.ms.starter.testing.MockitoUtils.once;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageServiceTest implements I18nTest {


  private SymphonyMessageService messageService;

  private MeterManager meterManager;
  private EmpClient empClient;
  private AuthenticationService authenticationService;
  private PodConfiguration podConfiguration;
  private BotConfiguration botConfiguration;
  private FederatedAccountRepository federatedAccountRepository;
  private DatafeedSessionPool datafeedSessionPool;
  private SymphonyMessageSender symphonyMessageSender;
  private AdminClient adminClient;
  private EmpSchemaService empSchemaService;
  private SymphonyService symphonyService;
  private ChannelService channelService;
  private StreamService streamService;
  private UsersInfoService usersInfoService;
  private MessageStatusService messageStatusService;
  private MessageEncryptor messageEncryptor;
  private MessageDecryptor messageDecryptor;
  private MessageIOMonitor messageIOMonitor;
  private EmpConfig empConfig;

  private SessionSupplier<SymphonySession> userSession;

  private SymphonySession botSession;

  private SymphonyAuthFactory symphonyAuthFactory;

  private static final long NOW = OffsetDateTime.now().toEpochSecond();
  public static final String FROM_SYMPHONY_USER_ID = "123456789";
  public static final String TO_SYMPHONY_USER_ID = "234567891";
  public static final String STREAM_ID_1 = "streamId_1";

  @BeforeEach
  public void setUp(MessageSource messageSource) throws NoSuchAlgorithmException {
    meterManager = new MeterManager(new SimpleMeterRegistry(), Optional.empty());
    empClient = mock(EmpClient.class);
    federatedAccountRepository = mock(FederatedAccountRepository.class);
    datafeedSessionPool = mock(DatafeedSessionPool.class);
    authenticationService = mock(AuthenticationService.class);

    botConfiguration = new BotConfiguration();
    botConfiguration.setUsername("username");
    botConfiguration.setEmailAddress("emailAddress");
    botConfiguration.setPrivateKey(new PemResource("-----botConfigurationPrivateKey"));
    botConfiguration.setSymphonyId("1234567890");

    podConfiguration = new PodConfiguration();
    podConfiguration.setUrl("podUrl");
    podConfiguration.setSessionAuth("sessionAuth");
    podConfiguration.setKeyAuth("keyAuth");

    empConfig = new EmpConfig();
    Map<String, Integer> maxTextLengths = new HashMap<>();
    maxTextLengths.put("emp", 1000);
    empConfig.setMaxTextLength(maxTextLengths);

    symphonyMessageSender = mock(SymphonyMessageSender.class);
    symphonyService = mock(SymphonyService.class);

    streamService = mock(StreamService.class);


    adminClient = mock(AdminClient.class);
    when(adminClient.getEmpList()).thenReturn(new EmpList());

    empSchemaService = new EmpSchemaService(adminClient);

    channelService = mock(ChannelService.class);

    usersInfoService = mock(UsersInfoService.class);

    messageStatusService = mock(MessageStatusService.class);

    messageService = new SymphonyMessageService(empConfig, empClient, federatedAccountRepository, mock(ForwarderQueueConsumer.class), datafeedSessionPool, symphonyMessageSender, adminClient, empSchemaService, symphonyService, messageStatusService, podConfiguration, botConfiguration, authenticationService, streamService, new MessageIOMonitor(meterManager), messageSource, mock(MessageDecryptor.class));

    botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());

    messageEncryptor = mock(MessageEncryptor.class);
    messageDecryptor = mock(MessageDecryptor.class);
    messageIOMonitor = mock(MessageIOMonitor.class);
    symphonyAuthFactory = new SymphonyAuthFactory(authenticationService, null, podConfiguration, botConfiguration, null);
  }

  /**
   * Partially overwrite setup to test handlebars template
   *
   * @param messageSource
   */
  private void spySymphonyMessageSender(MessageSource messageSource) {
    TemplateLoader loader = new ClassPathTemplateLoader();
    loader.setPrefix("/templates");
    loader.setSuffix(".hbs");

    SymphonySystemMessageTemplateProcessor templateProcessor = new SymphonySystemMessageTemplateProcessor(new Handlebars(loader));

    MessageIOMonitor messageMetrics = new MessageIOMonitor(meterManager);
    // really instantiate SymphonyMessageSender to test Handlebars templates.
    symphonyMessageSender = spy(new SymphonyMessageSender(podConfiguration, datafeedSessionPool, federatedAccountRepository, streamService,  templateProcessor, messageMetrics, messageEncryptor, messageDecryptor, symphonyService, empSchemaService, messageSource));
    messageService = new SymphonyMessageService(empConfig, empClient, federatedAccountRepository, mock(ForwarderQueueConsumer.class), datafeedSessionPool, symphonyMessageSender, adminClient, empSchemaService, symphonyService, messageStatusService, podConfiguration, botConfiguration, authenticationService, streamService, new MessageIOMonitor(meterManager), messageSource, messageDecryptor);
  }
  @Test
  public void testMessageWithDisclaimer() {
    IUser fromSymphonyUser = newIUser("1");
    List<String> members = Arrays.asList("1", "101");
    FederatedAccount federatedAccount101 = newFederatedAccount("emp", "101");
    federatedAccount101.setFederatedUserId("fed");

    when(federatedAccountRepository.findBySymphonyId("101")).thenReturn(Optional.of(federatedAccount101));
    // fromSymphonyUser is advisor
    when(adminClient.canChat("1", "fed", "emp")).thenReturn(Optional.of(CanChatResponse.CAN_CHAT));

    long now = new Date().getTime();


    // Without disclaimer
    GatewaySocialMessage message = GatewaySocialMessage.builder().streamId("streamId").messageId("messageId").fromUser(fromSymphonyUser).members(members).timestamp(now).textContent("message").build();
    messageService.onIMMessage(message);

    InOrder orderVerifier = inOrder(empClient);
    orderVerifier.verify(empClient, never()).sendMessage("emp", "streamId", "messageId", fromSymphonyUser, Collections.singletonList(federatedAccount101), now, "message", "disclaimer", null, null);
    orderVerifier.verify(empClient, once()).sendMessage("emp", "streamId", "messageId", fromSymphonyUser, Collections.singletonList(federatedAccount101), now, "message", "", null, null);
    orderVerifier.verifyNoMoreInteractions();

    // With disclaimer
    GatewaySocialMessage messageWithDisclaimer = GatewaySocialMessage.builder().streamId("streamId").messageId("messageId").fromUser(fromSymphonyUser).members(members).timestamp(now).textContent("message").disclaimer("disclaimer").build();
    messageService.onIMMessage(messageWithDisclaimer);

    orderVerifier.verify(empClient, once()).sendMessage("emp", "streamId", "messageId", fromSymphonyUser, Collections.singletonList(federatedAccount101), now, "message", "disclaimer", null, null);
    orderVerifier.verify(empClient, never()).sendMessage("emp", "streamId", "messageId", fromSymphonyUser, Collections.singletonList(federatedAccount101), now, "message", "", null, null);
    orderVerifier.verifyNoMoreInteractions();
  }

  @Test
  void onMessageReply() throws NoSuchAlgorithmException {
    when(adminClient.canChat(FROM_SYMPHONY_USER_ID, "fed", "emp")).thenReturn(Optional.of(CanChatResponse.CAN_CHAT));
    FederatedAccount toFederatedAccount = buildDefaultToFederatedAccount();

    IUser fromSymphonyUser = buildDefaultFromUser();

    List<IAttachment> attachments = new ArrayList<>();

    attachments.add(new AttachmentEntity.Builder().withName("abc").withContentType("image/png").withFileId("123").build());

    when(federatedAccountRepository.findBySymphonyId(TO_SYMPHONY_USER_ID)).thenReturn(Optional.of(toFederatedAccount));
    GatewaySocialMessage message = GatewaySocialMessage.builder()
      .streamId("streamId")
      .messageId("messageId")
      .fromUser(fromSymphonyUser)
      .members(Arrays.asList(FROM_SYMPHONY_USER_ID, TO_SYMPHONY_USER_ID))
        .attachments(attachments)
        .timestamp(NOW)
      .textContent("12345ThisIsAMessage")
      .customEntities(Collections.singletonList(
        CustomEntity.builder()
          .type(CustomEntity.QUOTE_TYPE)
          .endIndex(5)
          .data(Map.of("id", "Rp6N+cRznOWTwnD80vg7OX///oOAgiSfbQ=="))
          .build()
      ))
      .build();

    SBEEventMessage sbeEventMessage = SBEEventMessage.builder().messageId("Rp6N+cRznOWTwnD80vg7OX///oOAgiSfbQ==").disclaimer("disclaimer1").text("123This is the inline message")
      .ingestionDate(123L)
      .parsedCustomEntities(Collections.singletonList(
          CustomEntity.builder()
            .type(CustomEntity.QUOTE_TYPE)
            .endIndex(3)
            .data(Map.of("id", "otherMsg"))
            .build()
        ))
      .from(
        SBEEventUser.builder()
        .firstName("first")
        .surName("last")
        .id(12345L)
        .company("company")
        .build())
      .attachments(Collections.singletonList(
        SBEMessageAttachment.builder().contentType("image/png").name("hello.png").build()
      ))
      .build();


    when(symphonyService.getEncryptedMessage(eq("Rp6N-cRznOWTwnD80vg7OX___oOAgiSfbQ"), any())).thenReturn(Optional.of(sbeEventMessage));

    SendmessagerequestInlineMessage sendmessagerequestInlineMessage = new SendmessagerequestInlineMessage()
      .messageId("Rp6N-cRznOWTwnD80vg7OX___oOAgiSfbQ")
      .text("This is the inline message")
      .timestamp(123L)
      .fromMember(new ChannelMember().firstName("first").lastName("last").symphonyId("12345").companyName("company"))
      .addAttachmentsItem(new AttachmentInfo().contentType("image/png").fileName("hello.png"));



    messageService.onIMMessage(message);

    verify(federatedAccountRepository, once()).findBySymphonyId(FROM_SYMPHONY_USER_ID);
    verify(federatedAccountRepository, once()).findBySymphonyId(TO_SYMPHONY_USER_ID);
    verify(empClient, once()).sendMessage("emp", "streamId", "messageId", fromSymphonyUser, Collections.singletonList(toFederatedAccount), NOW, "ThisIsAMessage", "", null, sendmessagerequestInlineMessage);
  }
  @Test
  void onMessageReplyToBot() {
    when(adminClient.canChat(FROM_SYMPHONY_USER_ID, "fed", "emp")).thenReturn(Optional.of(CanChatResponse.CAN_CHAT));
    FederatedAccount toFederatedAccount = buildDefaultToFederatedAccount();

    IUser fromSymphonyUser = buildDefaultFromUser();

    List<IAttachment> attachments = new ArrayList<>();

    attachments.add(new AttachmentEntity.Builder().withName("abc").withContentType("image/png").withFileId("123").build());

    when(federatedAccountRepository.findBySymphonyId(TO_SYMPHONY_USER_ID)).thenReturn(Optional.of(toFederatedAccount));
    GatewaySocialMessage message = GatewaySocialMessage.builder()
      .streamId("streamId")
      .messageId("messageId")
      .fromUser(fromSymphonyUser)
      .members(Arrays.asList(FROM_SYMPHONY_USER_ID, TO_SYMPHONY_USER_ID))
        .attachments(attachments)
        .timestamp(NOW)
      .textContent("12345ThisIsAMessage")
      .customEntities(Collections.singletonList(
        CustomEntity.builder()
          .type(CustomEntity.QUOTE_TYPE)
          .endIndex(5)
          .data(Map.of("id", "Rp6N+cRznOWTwnD80vg7OX///oOAgiSfbQ=="))
          .build()
      ))
      .build();

    SBEEventMessage sbeEventMessage = SBEEventMessage.builder().messageId("Rp6N+cRznOWTwnD80vg7OX///oOAgiSfbQ==").disclaimer("disclaimer1").text("123This is the inline message")
      .ingestionDate(123L)
      .parsedCustomEntities(Collections.singletonList(
          CustomEntity.builder()
            .type(CustomEntity.QUOTE_TYPE)
            .endIndex(3)
            .data(Map.of("id", "otherMsg"))
            .build()
        ))
      .from(
        SBEEventUser.builder()
        .firstName("first")
        .surName("last")
        .id(Long.valueOf(botConfiguration.getSymphonyId()))
        .company("company")
        .build())
      .build();


    when(symphonyService.getEncryptedMessage(eq("Rp6N-cRznOWTwnD80vg7OX___oOAgiSfbQ"), any())).thenReturn(Optional.of(sbeEventMessage));

    SendmessagerequestInlineMessage sendmessagerequestInlineMessage = new SendmessagerequestInlineMessage()
      .messageId("Rp6N-cRznOWTwnD80vg7OX___oOAgiSfbQ")
      .text("This is the inline message")
      .timestamp(123L)
      .fromMember(new ChannelMember().firstName("first").lastName("last").symphonyId("12345").companyName("company"))
      .addAttachmentsItem(new AttachmentInfo().contentType("image/png").fileName("hello.png"));



    messageService.onIMMessage(message);

    verify(federatedAccountRepository, once()).findBySymphonyId(FROM_SYMPHONY_USER_ID);
    verify(federatedAccountRepository, once()).findBySymphonyId(TO_SYMPHONY_USER_ID);
    verify(empClient, never()).sendMessage("emp", "streamId", "messageId", message.getFromUser(), Collections.singletonList(toFederatedAccount), NOW, "text", "", null, null);
    // session is mocked, null for now
    verify(symphonyMessageSender, once()).sendAlertMessage(null, "streamId", "You can't reply to this type of message.", Collections.emptyList());
  }

  private static Stream<Arguments> textProvider() {
    return Stream.of(
      arguments("text", "text"),
      arguments("<b onclick=\"alert('hello')\">text</b>", "<b onclick=\"alert('hello')\">text</b>")
    );
  }

  @ParameterizedTest
  @MethodSource("textProvider")
  void onIMMessage(String inputText, String expectedSentText) {
    when(adminClient.canChat(FROM_SYMPHONY_USER_ID, "fed", "emp")).thenReturn(Optional.of(CanChatResponse.CAN_CHAT));
    FederatedAccount toFederatedAccount = buildDefaultToFederatedAccount();

    IUser fromSymphonyUser = buildDefaultFromUser();

    when(federatedAccountRepository.findBySymphonyId(TO_SYMPHONY_USER_ID)).thenReturn(Optional.of(toFederatedAccount));
    GatewaySocialMessage message = GatewaySocialMessage.builder().streamId("streamId").messageId("messageId").fromUser(fromSymphonyUser).members(Arrays.asList(FROM_SYMPHONY_USER_ID, TO_SYMPHONY_USER_ID)).timestamp(NOW).textContent(inputText).build();
    messageService.onIMMessage(message);

    verify(federatedAccountRepository, once()).findBySymphonyId(FROM_SYMPHONY_USER_ID);
    verify(federatedAccountRepository, once()).findBySymphonyId(TO_SYMPHONY_USER_ID);
    verify(empClient, once()).sendMessage("emp", "streamId", "messageId", fromSymphonyUser, Collections.singletonList(toFederatedAccount), NOW, expectedSentText, "", null, null);
  }

  @Test
  void onIMMessage_No_entitlements() {
    when(adminClient.canChat(FROM_SYMPHONY_USER_ID, "fed", "emp")).thenReturn(Optional.of(CanChatResponse.NO_ENTITLEMENT));
    FederatedAccount toFederatedAccount = FederatedAccount.builder()
      .emp("emp")
      .symphonyUserId(TO_SYMPHONY_USER_ID)
      .federatedUserId("fed")
      .build();

    IUser fromSymphonyUser = newIUser(FROM_SYMPHONY_USER_ID);

    when(federatedAccountRepository.findBySymphonyId(TO_SYMPHONY_USER_ID)).thenReturn(Optional.of(toFederatedAccount));
    GatewaySocialMessage message = GatewaySocialMessage.builder().streamId("streamId").messageId("messageId").fromUser(fromSymphonyUser).members(Arrays.asList(FROM_SYMPHONY_USER_ID, TO_SYMPHONY_USER_ID)).timestamp(NOW).textContent("text").build();
    messageService.onIMMessage(message);

    verify(federatedAccountRepository, once()).findBySymphonyId(FROM_SYMPHONY_USER_ID);
    verify(federatedAccountRepository, once()).findBySymphonyId(TO_SYMPHONY_USER_ID);
    verify(empClient, never()).sendMessage("emp", "streamId", "messageId", fromSymphonyUser, Collections.singletonList(toFederatedAccount), NOW, "text", "", null, null);
    // session is mocked, null for now
    verify(symphonyMessageSender, once()).sendAlertMessage(null, "streamId", "You are not permitted to send messages to emp users.", Collections.emptyList());
  }

  @Test
  void onIMMessage_No_PartiallySent() throws GeneralSecurityException {
    when(adminClient.canChat(FROM_SYMPHONY_USER_ID, "fed", "emp")).thenReturn(Optional.of(CanChatResponse.NO_ENTITLEMENT));
    String id1 = TO_SYMPHONY_USER_ID + "1";
    String id2 = TO_SYMPHONY_USER_ID + "2";
    FederatedAccount toFedAcc1 = FederatedAccount.builder()
      .emp("emp")
      .symphonyUserId(id1)
      .federatedUserId("fed1")
      .firstName("firstName 1")
      .lastName("lastName 1")
      .build();
    FederatedAccount toFedAcc2 = FederatedAccount.builder()
      .emp("emp")
      .symphonyUserId(id2)
      .federatedUserId("fed2")
      .firstName("firstName 2")
      .lastName("lastName 2")
      .build();

    IUser fromSymphonyUser = newIUser(FROM_SYMPHONY_USER_ID);
    when(adminClient.canChat(FROM_SYMPHONY_USER_ID, id1, "emp")).thenReturn(Optional.of(CanChatResponse.CAN_CHAT));
    when(adminClient.canChat(FROM_SYMPHONY_USER_ID, id2, "emp")).thenReturn(Optional.of(CanChatResponse.CAN_CHAT));

    when(federatedAccountRepository.findBySymphonyId(id1)).thenReturn(Optional.of(toFedAcc1));
    when(federatedAccountRepository.findBySymphonyId(id2)).thenReturn(Optional.of(toFedAcc2));


    List<OperationIdBySymId> empResult = List.of(
      new OperationIdBySymId().symphonyId(toFedAcc1.getSymphonyUserId()).operationId("leaseId"),
      // no operation ID ==> message not sent for user 2
      new OperationIdBySymId().symphonyId(toFedAcc2.getSymphonyUserId()));
    SendMessageResponse sendMessageResponse = new SendMessageResponse().operationIds(empResult);
    when(empClient.sendMessage("emp", "streamId", "messageId", fromSymphonyUser, List.of(toFedAcc1, toFedAcc2), NOW, "text", "", null, null)).thenReturn(Optional.of(sendMessageResponse));
    GatewaySocialMessage message = GatewaySocialMessage.builder().streamId("streamId").messageId("messageId").fromUser(fromSymphonyUser).members(Arrays.asList(id1, id2, TO_SYMPHONY_USER_ID)).timestamp(NOW).textContent("text").chatType("CHATROOM").build();

    messageService.onIMMessage(message);
    // session is mocked, null for now
    SessionSupplier<SymphonySession> symphonySession = null;// new SessionSupplier<>("username", new SymphonyRsaAuthFunction(authenticationService, podConfiguration, parseRSAPrivateKey(chatConfiguration.getSharedPrivateKey().getData())));

    verify(symphonyMessageSender, once()).sendAlertMessage(symphonySession, "streamId", "Some users did not received the message (messageId : messageId): firstName 2 lastName 2.", null);
  }

  @Test
  void onIMMessage_No_PartiallySent_WithErrors(MessageSource messageSource) throws GeneralSecurityException {
    spySymphonyMessageSender(messageSource);

    when(adminClient.canChat(FROM_SYMPHONY_USER_ID, "fed", "emp")).thenReturn(Optional.of(CanChatResponse.NO_ENTITLEMENT));
    String id1 = TO_SYMPHONY_USER_ID + "1";
    String id2 = TO_SYMPHONY_USER_ID + "2";
    FederatedAccount toFedAcc1 = FederatedAccount.builder()
      .emp("emp")
      .symphonyUserId(id1)
      .federatedUserId("fed1")
      .firstName("firstName 1")
      .lastName("lastName 1")
      .build();
    FederatedAccount toFedAcc2 = FederatedAccount.builder()
      .emp("emp")
      .symphonyUserId(id2)
      .federatedUserId("fed2")
      .firstName("firstName 2")
      .lastName("lastName 2")
      .build();

    IUser fromSymphonyUser = newIUser(FROM_SYMPHONY_USER_ID);
    when(adminClient.canChat(FROM_SYMPHONY_USER_ID, id1, "emp")).thenReturn(Optional.of(CanChatResponse.CAN_CHAT));
    when(adminClient.canChat(FROM_SYMPHONY_USER_ID, id2, "emp")).thenReturn(Optional.of(CanChatResponse.CAN_CHAT));

    when(federatedAccountRepository.findBySymphonyId(id1)).thenReturn(Optional.of(toFedAcc1));
    when(federatedAccountRepository.findBySymphonyId(id2)).thenReturn(Optional.of(toFedAcc2));


    List<OperationIdBySymId> empResult = List.of(
      new OperationIdBySymId().symphonyId(toFedAcc1.getSymphonyUserId()).operationId("leaseId"),
      // no operation ID ==> message not sent for user 2
      new OperationIdBySymId().symphonyId(toFedAcc2.getSymphonyUserId()));


    List<EmpError> empErrors = List.of(
      // no operation ID ==> message not sent for user 2
      new EmpError().statusCode(400).detail("error coming directly from EMP"));

    SendMessageResponse sendMessageResponse = new SendMessageResponse().operationIds(empResult).errors(empErrors);
    when(empClient.sendMessage("emp", "streamId", "messageId", fromSymphonyUser, List.of(toFedAcc1, toFedAcc2), NOW, "text", "", null, null)).thenReturn(Optional.of(sendMessageResponse));
    GatewaySocialMessage message = GatewaySocialMessage.builder().streamId("streamId").messageId("messageId").fromUser(fromSymphonyUser).members(Arrays.asList(id1, id2, TO_SYMPHONY_USER_ID)).timestamp(NOW).textContent("text").chatType("CHATROOM").build();

    messageService.onIMMessage(message);
    // session is mocked, null for now
    verify(symphonyMessageSender, once()).sendAlertMessage(null, "streamId", "Some users did not received the message (messageId : messageId): firstName 2 lastName 2.",
      Arrays.asList("error coming directly from EMP"));
    String expectedMessage = "<messageML>\n" +
      "  <div>\n" +
      "    <card data-accent-color=\"tempo-bg-color--red\"\n" +
      "          data-icon-src=\"data:image/png;base64,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\">\n" +
      "\n" +
      "\n" +
      "        <body>\n" +
      "        \n" +
      "         \n" +
      "           Some users did not received the message (messageId : messageId): firstName 2 lastName 2.\n" +
      "            \n" +
      "                <br /><span class=\"tempo-text-color--secondary\">Errors:</span>\n" +
      "              \n" +
      "                <br /><span class=\"hashTag\" style=\"margin-right:24px\">error coming directly from EMP</span>\n" +
      "              \n" +
      "            \n" +
      "         \n" +
      "        </body>\n" +
      "\n" +
      "    </card>\n" +
      "  </div>\n" +
      "</messageML>\n";
    verify(streamService, once()).sendMessage(eq("podUrl"), any(), eq("streamId"), eq(expectedMessage));
  }

  @Test
  void onIMMessage_No_Contact() {
    when(adminClient.canChat(FROM_SYMPHONY_USER_ID, "fed", "emp")).thenReturn(Optional.of(CanChatResponse.NO_CONTACT));
    FederatedAccount toFederatedAccount = FederatedAccount.builder()
      .emp("emp")
      .symphonyUserId(TO_SYMPHONY_USER_ID)
      .federatedUserId("fed")
      .build();

    IUser fromSymphonyUser = newIUser(FROM_SYMPHONY_USER_ID);

    when(federatedAccountRepository.findBySymphonyId(TO_SYMPHONY_USER_ID)).thenReturn(Optional.of(toFederatedAccount));
    GatewaySocialMessage message = GatewaySocialMessage.builder().streamId("streamId").messageId("messageId").fromUser(fromSymphonyUser).members(Arrays.asList(FROM_SYMPHONY_USER_ID, TO_SYMPHONY_USER_ID)).timestamp(NOW).textContent("text").build();
    messageService.onIMMessage(message);

    verify(federatedAccountRepository, once()).findBySymphonyId(FROM_SYMPHONY_USER_ID);
    verify(federatedAccountRepository, once()).findBySymphonyId(TO_SYMPHONY_USER_ID);
    verify(empClient, never()).sendMessage("emp", "streamId", "messageId", fromSymphonyUser, Collections.singletonList(toFederatedAccount), NOW, "text", "", null, null);
    // session is mocked, null for now
    verify(symphonyMessageSender, once()).sendAlertMessage(null, "streamId", "Sorry, you're not permitted to chat with this user.", Collections.emptyList());
  }

  private static IUser buildDefaultFromUser() {
    return newIUser(FROM_SYMPHONY_USER_ID);
  }

  private static FederatedAccount buildDefaultToFederatedAccount() {
    return FederatedAccount.builder()
      .emp("emp")
      .symphonyUserId(TO_SYMPHONY_USER_ID)
      .federatedUserId("fed")
      .build();
  }

  private static Stream<Arguments> unsupportedGatewaySocialMessageProvider() {
    IUser fromSymphonyUser = buildDefaultFromUser();
    FederatedAccount toFederatedAccount = buildDefaultToFederatedAccount();
    return Stream.of(
      arguments(toFederatedAccount,
        GatewaySocialMessage.builder().streamId("streamId").messageId("messageId").fromUser(fromSymphonyUser).members(Arrays.asList(FROM_SYMPHONY_USER_ID, TO_SYMPHONY_USER_ID)).timestamp(NOW).textContent("text").chime(true).build(),
        "You cannot chime your contacts here."),
      arguments(toFederatedAccount,
        GatewaySocialMessage.builder().streamId("streamId").messageId("messageId").fromUser(fromSymphonyUser).members(Arrays.asList(FROM_SYMPHONY_USER_ID, TO_SYMPHONY_USER_ID)).timestamp(NOW).textContent("text").table(true).build(),
        "Sorry, you can't send tables here.")
    );
  }

  @ParameterizedTest
  @MethodSource("unsupportedGatewaySocialMessageProvider")
  void onIMMessage_UnsupportedContents(FederatedAccount toFederatedAccount, GatewaySocialMessage message, String expectedAlertMessage) {
    when(federatedAccountRepository.findBySymphonyId(TO_SYMPHONY_USER_ID)).thenReturn(Optional.of(toFederatedAccount));
    //when(empClient.sendMessage("emp", "streamId", "messageId", fromSymphonyUser, toFederatedAccount, NOW, "text", null, null)).thenReturn(Optional.of("leaseId"));
    messageService.onIMMessage(message);

    verify(empClient, never()).sendMessage("emp", "streamId", "messageId", message.getFromUser(), Collections.singletonList(toFederatedAccount), NOW, "text", "", null, null);
    // session is mocked, null for now
    verify(symphonyMessageSender, once()).sendAlertMessage(null, "streamId", expectedAlertMessage, Collections.emptyList());   }

  @Test
  void onIMMessage_FederatedServiceAccountNotFound() {
    // is this still useful??
    IUser fromSymphonyUser = newIUser(FROM_SYMPHONY_USER_ID);

    when(federatedAccountRepository.findBySymphonyId(FROM_SYMPHONY_USER_ID)).thenReturn(Optional.empty());
    GatewaySocialMessage message = GatewaySocialMessage.builder().streamId("streamId").messageId("messageId").fromUser(fromSymphonyUser).members(Arrays.asList(FROM_SYMPHONY_USER_ID, TO_SYMPHONY_USER_ID)).timestamp(NOW).textContent("text").build();
    Assertions.assertDoesNotThrow(() -> messageService.onIMMessage(message));
  }

  @Test
  void sendMessage() {
    FederatedAccount fromFederatedAccount = FederatedAccount.builder()
      .emp("emp")
      .symphonyUserId(FROM_SYMPHONY_USER_ID)
      .federatedUserId("fed")
      .build();
    when(federatedAccountRepository.findBySymphonyId(FROM_SYMPHONY_USER_ID)).thenReturn(Optional.of(fromFederatedAccount));
    when(adminClient.canChat(TO_SYMPHONY_USER_ID, "fed", "emp")).thenReturn(Optional.of(CanChatResponse.CAN_CHAT));
    StreamAttributes streamAttributes = StreamAttributes.builder().members(Arrays.asList(Long.valueOf(FROM_SYMPHONY_USER_ID), Long.valueOf(TO_SYMPHONY_USER_ID))).build();
    StreamInfo streamInfo = StreamInfo.builder().streamAttributes(streamAttributes).streamType(new StreamType(StreamTypes.IM)).build();
    when(streamService.getStreamInfo(anyString(), any(), eq("streamId"))).thenReturn(Optional.of(streamInfo));
    when(symphonyMessageSender.sendRawMessage(anyString(), anyString(), anyString(), any())).thenReturn(Optional.of("msgId"));
    messageService.sendMessage("streamId", FROM_SYMPHONY_USER_ID, null, "text", null, false, null, false, Optional.empty());
    verify(symphonyMessageSender, once()).sendRawMessage("streamId", FROM_SYMPHONY_USER_ID, "<messageML>text</messageML>", null);
  }

  @Test
  void sendInlineReplyMessage() {
    FederatedAccount fromFederatedAccount = FederatedAccount.builder()
      .emp("emp")
      .symphonyUserId(FROM_SYMPHONY_USER_ID)
      .federatedUserId("fed")
      .build();
    when(federatedAccountRepository.findBySymphonyId(FROM_SYMPHONY_USER_ID)).thenReturn(Optional.of(fromFederatedAccount));
    when(adminClient.canChat(TO_SYMPHONY_USER_ID, "fed", "emp")).thenReturn(Optional.of(CanChatResponse.CAN_CHAT));
    StreamAttributes streamAttributes = StreamAttributes.builder().members(Arrays.asList(Long.valueOf(FROM_SYMPHONY_USER_ID), Long.valueOf(TO_SYMPHONY_USER_ID))).build();
    StreamInfo streamInfo = StreamInfo.builder().streamAttributes(streamAttributes).streamType(new StreamType(StreamTypes.IM)).build();
    when(streamService.getStreamInfo(anyString(), any(), eq("streamId"))).thenReturn(Optional.of(streamInfo));
    when(symphonyMessageSender.sendReplyMessage(anyString(), anyString(), anyString(), anyString(), any(Boolean.class), any(Optional.class))).thenReturn(Optional.of("msgId"));
    messageService.sendMessage("streamId", FROM_SYMPHONY_USER_ID, null, "text", null, false, "parent_message_id", false, Optional.empty());
    verify(symphonyMessageSender, once()).sendReplyMessage("streamId", FROM_SYMPHONY_USER_ID, "text", "parent_message_id", false, Optional.empty());
  }

  @Test
  void sendMessage_cantChatNoEntitlement() {
    FederatedAccount fromFederatedAccount = FederatedAccount.builder()
      .emp("emp")
      .symphonyUserId(FROM_SYMPHONY_USER_ID)
      .federatedUserId("fed")
      .build();
    when(federatedAccountRepository.findBySymphonyId(FROM_SYMPHONY_USER_ID)).thenReturn(Optional.of(fromFederatedAccount));
    when(adminClient.canChat(TO_SYMPHONY_USER_ID, "fed", "emp")).thenReturn(Optional.of(CanChatResponse.NO_ENTITLEMENT));
    StreamAttributes streamAttributes = StreamAttributes.builder().members(Arrays.asList(Long.valueOf(FROM_SYMPHONY_USER_ID), Long.valueOf(TO_SYMPHONY_USER_ID))).build();
    StreamInfo streamInfo = StreamInfo.builder().streamAttributes(streamAttributes).streamType(new StreamType(StreamTypes.IM)).build();
    when(streamService.getStreamInfo(anyString(), any(), eq("streamId"))).thenReturn(Optional.of(streamInfo));

    UserInfo userInfo = new UserInfo();
    userInfo.setDisplayName("display name");
    when(usersInfoService.getUsersFromIds(eq("podUrl"), any(SessionSupplier.class), eq(Collections.singletonList(TO_SYMPHONY_USER_ID)))).thenReturn(Collections.singletonList(userInfo));
    messageService.sendMessage("streamId", FROM_SYMPHONY_USER_ID, null, "text", null, false, null, false, Optional.empty());
    verify(empClient).sendSystemMessage(eq("emp"), eq("streamId"), eq(FROM_SYMPHONY_USER_ID), any(), eq("Sorry, this conversation is no longer available."), eq(TypeEnum.ALERT));
  }

  @Test
  void sendMessage_cantChatNoEntitlementUnknownAdvisor() {
    FederatedAccount fromFederatedAccount = FederatedAccount.builder()
      .emp("emp")
      .symphonyUserId(FROM_SYMPHONY_USER_ID)
      .federatedUserId("fed")
      .build();
    when(federatedAccountRepository.findBySymphonyId(FROM_SYMPHONY_USER_ID)).thenReturn(Optional.of(fromFederatedAccount));
    when(adminClient.canChat(TO_SYMPHONY_USER_ID, "fed", "emp")).thenReturn(Optional.of(CanChatResponse.NO_ENTITLEMENT));
    StreamAttributes streamAttributes = StreamAttributes.builder().members(Arrays.asList(Long.valueOf(FROM_SYMPHONY_USER_ID), Long.valueOf(TO_SYMPHONY_USER_ID))).build();
    StreamInfo streamInfo = StreamInfo.builder().streamAttributes(streamAttributes).streamType(new StreamType(StreamTypes.IM)).build();
    when(streamService.getStreamInfo(anyString(), any(), eq("streamId"))).thenReturn(Optional.of(streamInfo));
    botSession = new SymphonySession();
    when(usersInfoService.getUsersFromIds("podUrl", new StaticSessionSupplier<>(botSession), Collections.singletonList(TO_SYMPHONY_USER_ID))).thenReturn(Collections.emptyList());
    messageService.sendMessage("streamId", FROM_SYMPHONY_USER_ID, null, "text", null, false, null, false, Optional.empty());
    verify(empClient).sendSystemMessage(eq("emp"), eq("streamId"), eq(FROM_SYMPHONY_USER_ID), any(), eq("Sorry, this conversation is no longer available."), eq(TypeEnum.ALERT));
  }

  @Test
  void sendMessage_TextTooLong() {
    String tooLongMsg = RandomStringUtils.random(30001);
    FederatedAccount fromFederatedAccount = FederatedAccount.builder()
      .emp("emp")
      .symphonyUserId(FROM_SYMPHONY_USER_ID)
      .federatedUserId("fed")
      .build();
    when(federatedAccountRepository.findBySymphonyId(FROM_SYMPHONY_USER_ID)).thenReturn(Optional.of(fromFederatedAccount));
    when(adminClient.canChat(TO_SYMPHONY_USER_ID, "fed", "emp")).thenReturn(Optional.of(CanChatResponse.CAN_CHAT));
    StreamAttributes streamAttributes = StreamAttributes.builder().members(Arrays.asList(Long.valueOf(FROM_SYMPHONY_USER_ID), Long.valueOf(TO_SYMPHONY_USER_ID))).build();
    StreamInfo streamInfo = StreamInfo.builder().streamAttributes(streamAttributes).streamType(new StreamType(StreamTypes.IM)).build();
    when(streamService.getStreamInfo(anyString(), any(), eq("streamId"))).thenReturn(Optional.of(streamInfo));
    when(symphonyMessageSender.sendRawMessage(anyString(), anyString(), anyString(), any())).thenReturn(Optional.of("msgId"));
    when(empClient.sendSystemMessage(eq("emp"), eq("streamId"), any(), any(), anyString(), eq(TypeEnum.INFO))).thenReturn(Optional.of("leaseId"));
    when(symphonyMessageSender.sendInfoMessage(anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.of("msgId"));
    messageService.sendMessage("streamId", FROM_SYMPHONY_USER_ID, null, tooLongMsg, null, false, null, false, Optional.empty());
    String expectedTruncatedMsgML = "<messageML>" + tooLongMsg.substring(0, 1000) + "</messageML>";
    String warningMessage = "The message was too long and was truncated. Only the first 1,000 characters were delivered";
    verify(symphonyMessageSender, once()).sendAlertMessage(null, "streamId", warningMessage, Collections.emptyList());
    verify(symphonyMessageSender, once()).sendRawMessage("streamId", FROM_SYMPHONY_USER_ID, expectedTruncatedMsgML, null);
    verify(empClient, once()).sendSystemMessage(eq("emp"), eq("streamId"), any(), any(), eq(warningMessage), eq(TypeEnum.ALERT));
  }

  @Test
  void sendMessage_AdvisorDoesntExist() {
    FederatedAccount fromFederatedAccount = FederatedAccount.builder()
      .emp("emp")
      .symphonyUserId(FROM_SYMPHONY_USER_ID)
      .federatedUserId("fed")
      .build();
    when(federatedAccountRepository.findBySymphonyId(FROM_SYMPHONY_USER_ID)).thenReturn(Optional.of(fromFederatedAccount));
    when(adminClient.canChat(TO_SYMPHONY_USER_ID, "fed", "emp")).thenReturn(Optional.of(CanChatResponse.CAN_CHAT));
    StreamAttributes streamAttributes = StreamAttributes.builder().members(Collections.emptyList()).build();
    StreamInfo streamInfo = StreamInfo.builder().streamAttributes(streamAttributes).streamType(new StreamType(StreamTypes.IM)).build();
    when(streamService.getStreamInfo(anyString(), any(), eq("streamId"))).thenReturn(Optional.of(streamInfo));
    messageService.sendMessage("streamId", FROM_SYMPHONY_USER_ID, null, "text", null, false, null, false, Optional.empty());
    verify(empClient).sendSystemMessage(eq("emp"), eq("streamId"), eq(FROM_SYMPHONY_USER_ID), any(), eq("Sorry, this conversation is no longer available."), eq(TypeEnum.ALERT));
  }

  @Test
  void sendChime_NotSupported() {
    FederatedAccount toFederatedAccount = FederatedAccount.builder()
      .emp("emp")
      .symphonyUserId(TO_SYMPHONY_USER_ID)
      .federatedUserId("federatedUserId")
      .build();
    when(federatedAccountRepository.findBySymphonyId(TO_SYMPHONY_USER_ID)).thenReturn(Optional.of(toFederatedAccount));


    IUser fromSymphonyUser = newIUser(FROM_SYMPHONY_USER_ID);
    GatewaySocialMessage gatewaySocialMessage = GatewaySocialMessage.builder()
      .streamId(STREAM_ID_1)
      .fromUser(fromSymphonyUser)
      .members(Arrays.asList(FROM_SYMPHONY_USER_ID, TO_SYMPHONY_USER_ID))
      .chime(true)
      .build();

    messageService.onIMMessage(gatewaySocialMessage);

    verify(symphonyMessageSender, once()).sendAlertMessage(null, STREAM_ID_1, "You cannot chime your contacts here.", Collections.emptyList());
  }

  /*
  @Test
  void onMIMMessage_FromSymphony_ToEMPs() throws UnknownDatafeedUserException {
    EntitlementResponse response = new EntitlementResponse();
    when(adminClient.getEntitlementAccess("1", "emp1")).thenReturn(Optional.of(response));
    when(adminClient.getEntitlementAccess("1", "emp2")).thenReturn(Optional.of(response));

    FederatedAccount federatedAccount101 =  newFederatedAccount("emp1", "101");
    FederatedAccount federatedAccount102 =  newFederatedAccount("emp1", "102");
    FederatedAccount federatedAccount201 =  newFederatedAccount("emp2", "201");
    FederatedAccount federatedAccount202 =  newFederatedAccount("emp2", "202");

    List<FederatedAccount> federatedAccountsForEmp1 = Arrays.asList(federatedAccount101, federatedAccount102);
    List<FederatedAccount> federatedAccountsForEmp2 = Arrays.asList(federatedAccount201, federatedAccount202);

    List<String> mimMembers = Arrays.asList("1", "2", "3", "101", "102", "201", "202");
//    List<IUser> mimMembers = Arrays.asList(newIUser("1"), newIUser("2"), newIUser("101"), newIUser("102"), newIUser("201"), newIUser("202"));
    IUser fromSymphonyUser = newIUser("1");

    long now = OffsetDateTime.now().toEpochSecond();

    // Messsage sender is not a federated user --> messaging from Symphony to EMPs
    when(federatedAccountRepository.findBySymphonyId(fromSymphonyUser.getId().toString())).thenReturn(Optional.empty());

    // FederatedAccounts
    when(federatedAccountRepository.findBySymphonyId("101")).thenReturn(Optional.of(federatedAccount101));
    when(federatedAccountRepository.findBySymphonyId("102")).thenReturn(Optional.of(federatedAccount102));
    when(federatedAccountRepository.findBySymphonyId("201")).thenReturn(Optional.of(federatedAccount201));
    when(federatedAccountRepository.findBySymphonyId("202")).thenReturn(Optional.of(federatedAccount202));

    // Non Federated Accounts
    when(federatedAccountRepository.findBySymphonyId("2")).thenReturn(Optional.empty());
    when(federatedAccountRepository.findBySymphonyId("3")).thenReturn(Optional.empty());


    when(empClient.sendMessage("emp1", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp1, now, "text")).thenReturn(Optional.of("lease1"));
    when(empClient.sendMessage("emp2", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp2, now, "text")).thenReturn(Optional.of("lease2"));

    DatafeedSessionPool.DatafeedSession userSession101 = new DatafeedSessionPool.DatafeedSession(userSession, "101");
    when(datafeedSessionPool.refreshSession("101")).thenReturn(userSession101);
    DatafeedSessionPool.DatafeedSession userSession102 = new DatafeedSessionPool.DatafeedSession(userSession, "102");
    when(datafeedSessionPool.refreshSession("102")).thenReturn(userSession102);
    DatafeedSessionPool.DatafeedSession userSession201 = new DatafeedSessionPool.DatafeedSession(userSession, "201");
    when(datafeedSessionPool.refreshSession("201")).thenReturn(userSession201);
    DatafeedSessionPool.DatafeedSession userSession202 = new DatafeedSessionPool.DatafeedSession(userSession, "202");
    when(datafeedSessionPool.refreshSession("202")).thenReturn(userSession202);

    messageService.onIMMessage("streamId", "messageId", fromSymphonyUser, mimMembers, now, "text", null, null);

    InOrder orderVerifier = inOrder(federatedAccountRepository, datafeedSessionPool, empClient);

    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("1");
    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("2");
    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("3");
    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("101");
    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("102");
    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("201");
    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("202");


    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("101");
    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("102");
    orderVerifier.verify(empClient, once()).sendMessage("emp1", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp1, now, "text");

    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("201");
    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("202");
    orderVerifier.verify(empClient, once()).sendMessage("emp2", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp2, now, "text");

    orderVerifier.verifyNoMoreInteractions();
  }
*/

  @Test
  void onMIMMessage_FederatedServiceAccountNotFound() {
    // This seems obsolete ??
    IUser fromSymphonyUser = newIUser(FROM_SYMPHONY_USER_ID);

    // Do not find any FederatedAccount
    when(federatedAccountRepository.findBySymphonyId(anyString())).thenReturn(Optional.empty());
    GatewaySocialMessage message = GatewaySocialMessage.builder().streamId("streamId").messageId("messageId").fromUser(fromSymphonyUser).members(Arrays.asList(FROM_SYMPHONY_USER_ID, TO_SYMPHONY_USER_ID, "345678912", "456789123")).timestamp(NOW).textContent("text").build();
    Assertions.assertDoesNotThrow(() -> messageService.onIMMessage(message));
  }

  /*
  @Test
  void onMIMMessage_FromSymphony_ToEMPs_UnknownDatafeed() throws UnknownDatafeedUserException {

    FederatedAccount federatedAccount101 = newFederatedAccount("emp1", "101");
    FederatedAccount federatedAccount102 = newFederatedAccount("emp1", "102");
    FederatedAccount federatedAccount201 = newFederatedAccount("emp2", "201");
    FederatedAccount federatedAccount202 = newFederatedAccount("emp2", "202");

    List<FederatedAccount> federatedAccountsForEmp1 = Arrays.asList(federatedAccount101, federatedAccount102);
    List<FederatedAccount> federatedAccountsForEmp2 = Arrays.asList(federatedAccount201, federatedAccount202);

    // Contains everyone: sender, federated and non-federated users
    List<String> mimMembers = Arrays.asList("1", "2", "3", "101", "102", "201", "202");
    IUser fromSymphonyUser = newIUser("1");

    long now = OffsetDateTime.now().toEpochSecond();

    // Messsage sender is not a federated user --> messaging from Symphony to EMPs
    when(federatedAccountRepository.findBySymphonyId(fromSymphonyUser.getId().toString())).thenReturn(Optional.empty());

    // FederatedAccounts
    when(federatedAccountRepository.findBySymphonyId("101")).thenReturn(Optional.of(federatedAccount101));
    when(federatedAccountRepository.findBySymphonyId("102")).thenReturn(Optional.of(federatedAccount102));
    when(federatedAccountRepository.findBySymphonyId("201")).thenReturn(Optional.of(federatedAccount201));
    when(federatedAccountRepository.findBySymphonyId("202")).thenReturn(Optional.of(federatedAccount202));

    // Non Federated Accounts
    when(federatedAccountRepository.findBySymphonyId("2")).thenReturn(Optional.empty());
    when(federatedAccountRepository.findBySymphonyId("3")).thenReturn(Optional.empty());


    when(empClient.sendMessage("emp1", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp1, now, "text")).thenReturn(Optional.of("lease1"));
    when(empClient.sendMessage("emp2", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp2, now, "text")).thenReturn(Optional.of("lease2"));

    DatafeedSessionPool.DatafeedSession userSession101 = new DatafeedSessionPool.DatafeedSession(userSession, "101");
    when(datafeedSessionPool.refreshSession("101")).thenReturn(userSession101);
    DatafeedSessionPool.DatafeedSession userSession102 = new DatafeedSessionPool.DatafeedSession(userSession, "102");
    when(datafeedSessionPool.refreshSession("102")).thenReturn(userSession102);
    DatafeedSessionPool.DatafeedSession userSession201 = new DatafeedSessionPool.DatafeedSession(userSession, "201");
    // userSession201 is unknown
    when(datafeedSessionPool.refreshSession("201")).thenThrow(UnknownDatafeedUserException.class);
    DatafeedSessionPool.DatafeedSession userSession202 = new DatafeedSessionPool.DatafeedSession(userSession, "202");
    when(datafeedSessionPool.refreshSession("202")).thenReturn(userSession202);

    IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class, () -> messageService.onIMMessage("streamId", "messageId", fromSymphonyUser, mimMembers, now, "text", null, null));
    assertEquals(UnknownDatafeedUserException.class, exception.getCause().getClass());

    InOrder orderVerifier = inOrder(federatedAccountRepository, datafeedSessionPool, empClient);

    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("1");
    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("2");
    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("3");
    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("101");
    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("102");
    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("201");
    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("202");


    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("101");
    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("102");
    orderVerifier.verify(empClient, once()).sendMessage("emp1", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp1, now, "text");
    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("201");

    // UserSession201 error -> whole process is aborted
    orderVerifier.verify(datafeedSessionPool, never()).refreshSession("202");
    orderVerifier.verify(empClient, never()).sendMessage("emp2", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp2, now, "text");

    orderVerifier.verifyNoMoreInteractions();
  }

  @Test
  void onMIMMessage_ProblemWithOneEMP() throws UnknownDatafeedUserException {

    FederatedAccount federatedAccount101 = newFederatedAccount("emp1", "101");
    FederatedAccount federatedAccount102 = newFederatedAccount("emp1", "102");
    FederatedAccount federatedAccount201 = newFederatedAccount("emp2", "201");
    FederatedAccount federatedAccount202 = newFederatedAccount("emp2", "202");
    FederatedAccount federatedAccount301 = newFederatedAccount("emp3", "301");
    FederatedAccount federatedAccount302 = newFederatedAccount("emp3", "302");

    List<FederatedAccount> federatedAccountsForEmp1 = Arrays.asList(federatedAccount101, federatedAccount102);
    List<FederatedAccount> federatedAccountsForEmp2 = Arrays.asList(federatedAccount201, federatedAccount202);
    List<FederatedAccount> federatedAccountsForEmp3 = Arrays.asList(federatedAccount301, federatedAccount302);

    // Contains everyone: sender, federated and non-federated users
    List<String> mimMembers = Arrays.asList("1", "2", "3", "101", "102", "201", "202", "301", "302");
    IUser fromSymphonyUser = newIUser("1");

    long now = OffsetDateTime.now().toEpochSecond();

    // Messsage sender is not a federated user --> messaging from Symphony to EMPs
    when(federatedAccountRepository.findBySymphonyId(fromSymphonyUser.getId().toString())).thenReturn(Optional.empty());

    // FederatedAccounts
    when(federatedAccountRepository.findBySymphonyId("101")).thenReturn(Optional.of(federatedAccount101));
    when(federatedAccountRepository.findBySymphonyId("102")).thenReturn(Optional.of(federatedAccount102));
    when(federatedAccountRepository.findBySymphonyId("201")).thenReturn(Optional.of(federatedAccount201));
    when(federatedAccountRepository.findBySymphonyId("202")).thenReturn(Optional.of(federatedAccount202));
    when(federatedAccountRepository.findBySymphonyId("301")).thenReturn(Optional.of(federatedAccount301));
    when(federatedAccountRepository.findBySymphonyId("302")).thenReturn(Optional.of(federatedAccount302));

    // Non Federated Accounts
    when(federatedAccountRepository.findBySymphonyId("2")).thenReturn(Optional.empty());
    when(federatedAccountRepository.findBySymphonyId("3")).thenReturn(Optional.empty());


    when(empClient.sendMessage("emp1", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp1, now, "text")).thenReturn(Optional.of("lease1"));
    when(empClient.sendMessage("emp2", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp2, now, "text")).thenThrow(RuntimeException.class);
    when(empClient.sendMessage("emp3", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp3, now, "text")).thenReturn(Optional.of("lease3"));

    DatafeedSessionPool.DatafeedSession userSession101 = new DatafeedSessionPool.DatafeedSession(userSession, "101");
    when(datafeedSessionPool.refreshSession("101")).thenReturn(userSession101);
    DatafeedSessionPool.DatafeedSession userSession102 = new DatafeedSessionPool.DatafeedSession(userSession, "102");
    when(datafeedSessionPool.refreshSession("102")).thenReturn(userSession102);
    DatafeedSessionPool.DatafeedSession userSession201 = new DatafeedSessionPool.DatafeedSession(userSession, "201");
    when(datafeedSessionPool.refreshSession("201")).thenReturn(userSession201);
    DatafeedSessionPool.DatafeedSession userSession202 = new DatafeedSessionPool.DatafeedSession(userSession, "202");
    when(datafeedSessionPool.refreshSession("202")).thenReturn(userSession202);
    DatafeedSessionPool.DatafeedSession userSession301 = new DatafeedSessionPool.DatafeedSession(userSession, "301");
    when(datafeedSessionPool.refreshSession("301")).thenReturn(userSession301);
    DatafeedSessionPool.DatafeedSession userSession302 = new DatafeedSessionPool.DatafeedSession(userSession, "302");
    when(datafeedSessionPool.refreshSession("302")).thenReturn(userSession302);

    RuntimeException exception = assertThrows(RuntimeException.class, () -> messageService.onIMMessage("streamId", "messageId", fromSymphonyUser, mimMembers, now, "text", null, null));

    InOrder orderVerifier = inOrder(federatedAccountRepository, datafeedSessionPool, empClient);

    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("1");
    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("2");
    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("3");
    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("101");
    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("102");
    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("201");
    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("202");
    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("301");
    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("302");

    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("101");
    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("102");
    orderVerifier.verify(empClient, once()).sendMessage("emp1", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp1, now, "text");
    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("201");
    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("202");
    orderVerifier.verify(empClient, once()).sendMessage("emp2", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp2, now, "text");
    // No interaction from here
    orderVerifier.verify(datafeedSessionPool, never()).refreshSession("301");
    orderVerifier.verify(datafeedSessionPool, never()).refreshSession("302");
    orderVerifier.verify(empClient, never()).sendMessage("emp3", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp3, now, "text");

    orderVerifier.verifyNoMoreInteractions();
  }
   */

  private static IUser newIUser(String symphonyUserId) {

    PodAndUserId id = PodAndUserId.newBuilder().build(Long.valueOf(symphonyUserId));
    IUser mockIUser = mock(IUser.class);
    when(mockIUser.getId()).thenReturn(id);
    when(mockIUser.getCompany()).thenReturn("symphony");

    return mockIUser;

  }

  private static FederatedAccount newFederatedAccount(String emp, String symphonyUserId) {
    return FederatedAccount.builder()
      .emp(emp)
      .symphonyUserId(symphonyUserId)
      .build();
  }
}
