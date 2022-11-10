package com.symphony.sfs.ms.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.symphony.oss.models.chat.canon.IAttachment;
import com.symphony.oss.models.chat.canon.IMaestroMessage;
import com.symphony.oss.models.chat.canon.MaestroEventType;
import com.symphony.oss.models.chat.canon.facade.ISocialMessage;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.admin.generated.model.AdvisorUpdateRequest;
import com.symphony.sfs.ms.admin.generated.model.AdvisorUpdateRequestItem;
import com.symphony.sfs.ms.admin.generated.model.CanChatResponse;
import com.symphony.sfs.ms.admin.generated.model.EmpEntity;
import com.symphony.sfs.ms.admin.generated.model.EmpList;
import com.symphony.sfs.ms.chat.api.util.AbstractIntegrationTest;
import com.symphony.sfs.ms.chat.config.EmpConfig;
import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.datafeed.GatewaySocialMessage;
import com.symphony.sfs.ms.chat.datafeed.MessageDecryptor;
import com.symphony.sfs.ms.chat.datafeed.ParentRelationshipType;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.ChannelRepository;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.chat.service.external.DefaultAdminClient;
import com.symphony.sfs.ms.chat.service.external.MockEmpClient;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyService;
import com.symphony.sfs.ms.starter.config.JacksonConfiguration;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.config.properties.common.PemResource;
import com.symphony.sfs.ms.starter.security.SessionManager;
import com.symphony.sfs.ms.starter.security.SessionSupplier;
import com.symphony.sfs.ms.starter.security.StaticSessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.message.MessageStatusService;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.symphony.tds.TenantDetailEntity;
import com.symphony.sfs.ms.starter.util.RsaUtils;
import model.UserInfo;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.symphony.sfs.ms.starter.testing.MockitoUtils.once;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PreventMIMRoomCreationTest extends AbstractIntegrationTest {

  protected ForwarderQueueConsumer forwarderQueueConsumer;
  protected AdvisorService advisorService;
  private SymphonyMessageSender symphonyMessageSender;
  private MockEmpClient empClient;
  private DatafeedSessionPool datafeedSessionPool;
  private SymphonyService symphonyService;
  private SymphonyMessageService symphonyMessageService;
  private AdminClient adminClient;
  private MessageDecryptor messageDecryptor;

  @BeforeEach
  public void setUp(MessageSource messageSource) throws Exception {
    symphonyService = mock(SymphonyService.class);
    StreamService streamService = mock(StreamService.class);
    symphonyMessageSender = mock(SymphonyMessageSender.class);
    empClient = new MockEmpClient();
    datafeedSessionPool = mock(DatafeedSessionPool.class);
    ChannelRepository channelRepository = mock(ChannelRepository.class);


    BotConfiguration botConfiguration = new BotConfiguration();
    botConfiguration.setUsername("username");
    botConfiguration.setEmailAddress("emailAddress");
    botConfiguration.setPrivateKey(new PemResource("-----botConfigurationPrivateKey"));
    botConfiguration.setSymphonyId("1234567890");

    PodConfiguration podConfiguration = new PodConfiguration();
    podConfiguration.setId("196");
    podConfiguration.setUrl("podUrl");
    podConfiguration.setSessionAuth("sessionAuth");
    podConfiguration.setKeyAuth("keyAuth");

    EmpConfig empConfig = new EmpConfig();
    Map<String, Integer> maxTextLengths = new HashMap<>();
    maxTextLengths.put("emp", 1000);
    empConfig.setMaxTextLength(maxTextLengths);

    SymphonySession userSession = new SymphonySession("username", "kmToken", "sessionToken");
    when(authenticationService.authenticate(anyString(), anyString(), anyString(), any(PrivateKey.class))).thenReturn(userSession);


    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair keyPair = kpg.generateKeyPair();

    ChatConfiguration chatConfiguration = new ChatConfiguration();
    chatConfiguration.setSharedPrivateKey(new PemResource(RsaUtils.encodeRSAKey(keyPair.getPrivate())));
    chatConfiguration.setSharedPublicKey(new PemResource(RsaUtils.encodeRSAKey(keyPair.getPublic())));

    adminClient = mock(DefaultAdminClient.class);
    FederatedAccountSessionService federatedAccountSessionService = new FederatedAccountSessionService(federatedAccountRepository);
    SessionManager sessionManager = new SessionManager(webClient, new ArrayList<>());
    datafeedSessionPool = new DatafeedSessionPool(chatConfiguration, federatedAccountSessionService, symphonyAuthFactory, sessionManager);
    ObjectMapper objectMapper = new JacksonConfiguration().configureJackson(new ObjectMapper());

    messageDecryptor = mock(MessageDecryptor.class);
    forwarderQueueConsumer = new ForwarderQueueConsumer(objectMapper, messageDecryptor, datafeedSessionPool, new MessageIOMonitor(meterManager), meterManager, botConfiguration, podConfiguration);

    when(adminClient.getEmpList()).thenReturn(new EmpList());
    EmpSchemaService empSchemaService = mock(EmpSchemaService.class);

    EmpEntity empEntity = new EmpEntity();
    when(empSchemaService.getEmpDefinition("WHATSAPP")).thenReturn(Optional.of(empEntity));
    when(empSchemaService.getEmpDisplayName("WHATSAPP")).thenReturn("WHATSAPP");

    MessageStatusService messageStatusService = mock(MessageStatusService.class);

    symphonyMessageService = spy(new SymphonyMessageService(empConfig, empClient, tenantDetailRepository, federatedAccountRepository, forwarderQueueConsumer, datafeedSessionPool, symphonyMessageSender, adminClient, empSchemaService, symphonyService, messageStatusService, podConfiguration, botConfiguration, streamService, new MessageIOMonitor(meterManager), messageSource, null));
    symphonyMessageService.registerAsDatafeedListener();

    ChannelService channelService = new ChannelService(symphonyMessageSender, empClient, forwarderQueueConsumer, datafeedSessionPool, federatedAccountRepository, empSchemaService, channelRepository, messageSource);
    channelService.registerAsDatafeedListener();

    advisorService = spy(new AdvisorService(tenantDetailRepository, forwarderQueueConsumer, adminClient));
    advisorService.registerAsDatafeedListener();
  }

  /////////////////////////////
  /// Event on customer Pod ///
  /////////////////////////////

  @Test
  public void onUserUpdated_byConnectBot() throws Exception {
    String notification = getSnsMaestroMessage("196", getEnvelopeMessage(getUpdateUserMaestroMessageByConnectBot(), MaestroEventType.UPDATE_USER, "196"));

    forwarderQueueConsumer.consumeCustomersPodEvents(notification);

    verify(advisorService, never()).onUserUpdated(any(IMaestroMessage.class), eq("196"));
  }


  @Test
  public void onUserUpdated_byCustomer_notUnknownPod() throws Exception {
    String notification = getSnsMaestroMessage("123", getEnvelopeMessage(getUpdateUserMaestroMessageByUser_Advisor(), MaestroEventType.UPDATE_USER, "123"));

    forwarderQueueConsumer.consumeCustomersPodEvents(notification);

    verify(advisorService).onUserUpdated(any(IMaestroMessage.class), eq("123"));

    verify(adminClient, never()).updateAdvisorInfo(any(AdvisorUpdateRequest.class));
  }

  @Test
  public void onUserUpdated_byCustomer_customerPod_advisor() throws Exception {
    tenantDetailRepository.save(new TenantDetailEntity("123", "podUrl", "SYM", Set.of("EMP1")));

    String notification = getSnsMaestroMessage("123", getEnvelopeMessage(getUpdateUserMaestroMessageByUser_Advisor(), MaestroEventType.UPDATE_USER, "123"));

    forwarderQueueConsumer.consumeCustomersPodEvents(notification);

    verify(advisorService).onUserUpdated(any(IMaestroMessage.class), eq("123"));

    AdvisorUpdateRequestItem item = new AdvisorUpdateRequestItem()
      .symphonyId("1234567891011")
      .firstName("FirstName")
      .lastName("LastName")
      .displayName("DisplayName")
      .companyName("Symphony")
      .avatar("../avatars/static/150/default.png")
      .podId("123");
    AdvisorUpdateRequest advisorUpdateRequest = new AdvisorUpdateRequest().addAdvisorsItem(item);

    verify(adminClient, once()).updateAdvisorInfo(advisorUpdateRequest);
  }

  @Test
  public void onUserUpdated_byCustomer_customerPod_bot() throws Exception {
    tenantDetailRepository.save(new TenantDetailEntity("123", "podUrl", "SYM", Set.of("EMP1")));

    String notification = getSnsMaestroMessage("123", getEnvelopeMessage(getUpdateUserMaestroMessageByUser_Bot(), MaestroEventType.UPDATE_USER, "123"));

    forwarderQueueConsumer.consumeCustomersPodEvents(notification);

    verify(advisorService).onUserUpdated(any(IMaestroMessage.class), eq("123"));

    AdvisorUpdateRequestItem item = new AdvisorUpdateRequestItem()
      .symphonyId("1234567891011")
      .displayName("DisplayName")
      .companyName("Symphony")
      .avatar("../avatars/static/150/default.png")
      .podId("123");
    AdvisorUpdateRequest advisorUpdateRequest = new AdvisorUpdateRequest().addAdvisorsItem(item);

    verify(adminClient, once()).updateAdvisorInfo(advisorUpdateRequest);
  }

  ///////////////////////////////
  /// Event on Federation Pod ///
  ///////////////////////////////

  @Test
  public void createMIMChannel() throws Exception {
    FederatedAccount whatsAppUserInvited = FederatedAccount.builder()
      .phoneNumber("+33612345678")
      .firstName("firstName")
      .lastName("lastName")
      .federatedUserId("federatedUserId")
      .emp("WHATSAPP")
      .symphonyUserId("3")
      .symphonyUsername("username")
      .build();

    UserInfo symphonyUserInvited = new UserInfo();
    symphonyUserInvited.setId(1L);

    UserInfo inviter = new UserInfo();
    inviter.setId(2L);

    federatedAccountRepository.save(whatsAppUserInvited);

    String notification = getSnsMaestroMessage("196", getEnvelopeMessage(getCreateMIMMaestroMessage(
      whatsAppUserInvited,
      symphonyUserInvited,
      inviter
    ), MaestroEventType.CREATE_IM, "196"));

    forwarderQueueConsumer.consume(notification, "1");
    assertEquals(0, empClient.getChannels().size());
  }

  @Test
  public void createIMChannel() throws Exception {
    FederatedAccount whatsAppUserInvited = FederatedAccount.builder()
      .phoneNumber("+33601020304")
      .firstName("firstName")
      .lastName("lastName")
      .federatedUserId("federatedUserId")
      .emp("WHATSAPP")
      .symphonyUserId("3")
      .symphonyUsername("username")
      .build();

    UserInfo inviter = new UserInfo();
    inviter.setId(2L);

    federatedAccountRepository.save(whatsAppUserInvited);

    String notification = getSnsMaestroMessage("196", getEnvelopeMessage(getCreateIMMaestroMessage(
      whatsAppUserInvited,
      inviter
    ), MaestroEventType.CREATE_IM, "196"));

    forwarderQueueConsumer.consume(notification, "1");

  }

  @Test
  public void onUserJoinedRoom() throws Exception {
    FederatedAccount whatsAppUserInvited = FederatedAccount.builder()
      .phoneNumber("+33601020304")
      .firstName("firstName")
      .lastName("lastName")
      .federatedUserId("federatedUserId")
      .emp("WHATSAPP")
      .symphonyUserId("1")
      .symphonyUsername("username")
      .build();

    UserInfo inviter = new UserInfo();
    inviter.setId(2L);

    SymphonySession session = datafeedSessionPool.openSession(whatsAppUserInvited);
    federatedAccountRepository.save(whatsAppUserInvited);

    String notification = getSnsMaestroMessage("196", getEnvelopeMessage(getCreateRoomMaestroMessage(
      whatsAppUserInvited,
      inviter
    ), MaestroEventType.JOIN_ROOM, "196"));

    forwarderQueueConsumer.consume(notification, "1");
  }

  @Test
  public void onIMMessageNotEntitled() throws Exception {
    FederatedAccount whatsAppUser = FederatedAccount.builder()
      .phoneNumber("+33601020304")
      .firstName("firstName")
      .lastName("lastName")
      .federatedUserId("federatedUserId")
      .emp("WHATSAPP")
      .symphonyUserId("3")
      .symphonyUsername("username")
      .build();

    UserInfo sender = new UserInfo();
    sender.setId(1L);

    federatedAccountRepository.save(whatsAppUser);
    SessionSupplier<SymphonySession> session = datafeedSessionPool.getSessionSupplier(whatsAppUser);

    String notification = getSnsSocialMessage("196", getEnvelopeMessage(getMIMMessageSocialMessage(
      whatsAppUser,
      sender
    ), MaestroEventType.CREATE_IM, "196"));
    doNothing().when(messageDecryptor).decrypt(any(ISocialMessage.class), eq(whatsAppUser.getSymphonyUserId()), any(), any());
    forwarderQueueConsumer.consume(notification, "1");

    verify(symphonyMessageSender, once()).sendAlertMessage(eq(session), eq("KdO82B8UMTU7og2M4vOFqn___pINMV_OdA"), eq("You are not permitted to send messages to WHATSAPP users."), eq(Collections.emptyList()));
  }


  /////////////////
  /// Messaging ///
  /////////////////

  @Test
  public void onIMMessage() throws Exception {
    FederatedAccount whatsAppUser = FederatedAccount.builder()
      .phoneNumber("+33601020304")
      .firstName("firstName")
      .lastName("lastName")
      .federatedUserId("federatedUserId")
      .emp("WHATSAPP")
      .symphonyUserId("3")
      .symphonyUsername("username")
      .build();

    UserInfo sender = new UserInfo();
    sender.setId(1L);

    UserInfo receipter = new UserInfo();
    receipter.setId(3L);

    SymphonySession session = datafeedSessionPool.openSession(whatsAppUser);
    federatedAccountRepository.save(whatsAppUser);

    String notification = getSnsSocialMessage("196", getEnvelopeSocialMessage(getMIMMessageSocialMessage(
      whatsAppUser,
      sender
    )));

    doAnswer(answer -> {
      GatewaySocialMessage message = answer.getArgument(3);
      message.setTextContent("message decrypted");
      return answer;
    }).when(messageDecryptor).decrypt(any(ISocialMessage.class), eq(whatsAppUser.getSymphonyUserId()), any(), any());
    when(adminClient.canChat("1", "federatedUserId", "WHATSAPP")).thenReturn(Optional.of(CanChatResponse.CAN_CHAT));
    when(authenticationService.getUserInfo(podConfiguration.getUrl(), new StaticSessionSupplier<>(session), true)).thenReturn(Optional.of(receipter));
    when(symphonyService.getAttachment(anyString(), anyString(), anyString(), any(SessionSupplier.class))).thenAnswer(ans -> ans.getArgument(2));

    forwarderQueueConsumer.consume(notification, "1");

    verify(symphonyMessageSender, never()).sendAlertMessage(any(), anyString(), anyString(), anyList());

    verify(symphonyMessageService, once()).onIMMessage(any(GatewaySocialMessage.class));
    verify(symphonyMessageService, once()).onIMMessage(argThat(arg -> {
      // As we cannot instantiate IUser and IAttachment we are not testing them so we check only the other fields and list size for attachments
      GatewaySocialMessage gatewayMessage = generateGatewaySocialMessage(arg.getFromUser(), arg.getAttachments(), Arrays.asList("3", "1"), Collections.singletonList("3"), "INSTANT_CHAT");
      assertEquals(2, arg.getAttachments().size());
      return arg.equals(gatewayMessage);
    }));
  }

  @Test
  public void onRoomMessage() throws Exception {
    FederatedAccount whatsAppUser = FederatedAccount.builder()
      .phoneNumber("+33601020304")
      .firstName("firstName")
      .lastName("lastName")
      .federatedUserId("federatedUserId")
      .emp("WHATSAPP")
      .symphonyUserId("3")
      .symphonyUsername("username")
      .build();

    UserInfo sender = new UserInfo();
    sender.setId(1L);

    UserInfo receipter = new UserInfo();
    receipter.setId(3L);

    UserInfo bot = new UserInfo();
    bot.setId(1234567890L);

    SymphonySession session = datafeedSessionPool.openSession(whatsAppUser);
    federatedAccountRepository.save(whatsAppUser);

    String notification = getSnsSocialMessage("196", getEnvelopeSocialMessage(getRoomMessageSocialMessage(
      whatsAppUser,
      sender,
      bot
    )));
    // this method is annoying to mock
    doAnswer(answer -> {
      GatewaySocialMessage message = answer.getArgument(3);
      message.setTextContent("message decrypted");
      return answer;
    }).when(messageDecryptor).decrypt(any(ISocialMessage.class), eq(whatsAppUser.getSymphonyUserId()), any(), any());
    when(authenticationService.getUserInfo(podConfiguration.getUrl(), new StaticSessionSupplier<>(session), true)).thenReturn(Optional.of(receipter));
    when(symphonyService.getAttachment(anyString(), anyString(), anyString(), any(SessionSupplier.class))).thenAnswer(ans -> ans.getArgument(2));
    long messageNumber = empClient.getMessages().size();
    forwarderQueueConsumer.consume(notification, "1");
    assertEquals(messageNumber + 1, empClient.getMessages().size());
    verify(symphonyMessageSender, never()).sendAlertMessage(any(), anyString(), anyString(), anyList());

    verify(symphonyMessageService, once()).onIMMessage(any(GatewaySocialMessage.class));
    verify(symphonyMessageService, once()).onIMMessage(argThat(arg -> {
      // As we cannot instantiate IUser and IAttachment we are not testing them so we check only the other fields and list size for attachments
      GatewaySocialMessage gatewayMessage = generateGatewaySocialMessage(arg.getFromUser(), arg.getAttachments(), Arrays.asList("3", "1", "1234567890"), Arrays.asList("3", "1234567890"), "CHATROOM");
      assertEquals(2, arg.getAttachments().size());
      return arg.equals(gatewayMessage);
    }));
  }

  @Test
  public void onRoomMessage_fromBot() throws Exception {
    FederatedAccount whatsAppUser = FederatedAccount.builder()
      .phoneNumber("+33601020304")
      .firstName("firstName")
      .lastName("lastName")
      .federatedUserId("federatedUserId")
      .emp("WHATSAPP")
      .symphonyUserId("3")
      .symphonyUsername("username")
      .build();

    UserInfo sender = new UserInfo();
    sender.setId(1L);

    UserInfo receipter = new UserInfo();
    receipter.setId(3L);

    UserInfo bot = new UserInfo();
    bot.setId(1234567890L);

    SymphonySession session = datafeedSessionPool.openSession(whatsAppUser);
    federatedAccountRepository.save(whatsAppUser);

    String notification = getSnsSocialMessage("196", getEnvelopeSocialMessage(getRoomMessageSocialMessage(
      whatsAppUser,
      bot,
      sender
    )));
    // this method is annoying to mock
    doAnswer(answer -> {
      GatewaySocialMessage message = answer.getArgument(3);
      message.setTextContent("message decrypted");
      return answer;
    }).when(messageDecryptor).decrypt(any(ISocialMessage.class), eq(whatsAppUser.getSymphonyUserId()), any(), any());
    when(authenticationService.getUserInfo(podConfiguration.getUrl(), new StaticSessionSupplier<>(session), true)).thenReturn(Optional.of(receipter));
    when(symphonyService.getAttachment(anyString(), anyString(), anyString(), any(SessionSupplier.class))).thenAnswer(ans -> ans.getArgument(2));
    long messageNumber = empClient.getMessages().size();
    forwarderQueueConsumer.consume(notification, "1");
    assertEquals(messageNumber, empClient.getMessages().size());
    verify(symphonyMessageSender, never()).sendAlertMessage(any(), anyString(), anyString(), anyList());

    verify(symphonyMessageService, once()).onIMMessage(any(GatewaySocialMessage.class));
    verify(symphonyMessageService, once()).onIMMessage(argThat(arg -> {
      // As we cannot instantiate IUser and IAttachment we are not testing them so we check only the other fields and list size for attachments
      GatewaySocialMessage gatewayMessage = generateGatewaySocialMessage(arg.getFromUser(), arg.getAttachments(), Arrays.asList("3", "1234567890", "1"), Collections.emptyList(), "CHATROOM");
      assertEquals(2, arg.getAttachments().size());
      return arg.equals(gatewayMessage);
    }));
  }


  private String getUpdateUserMaestroMessageByConnectBot(){
    return "{\n" +
      "  \"_type\":\"com.symphony.s2.model.chat.MaestroMessage\",\n" +
      "  \"affectedUsers\":[\n" +
      "    {\n" +
      "      \"accountType\":\"SYSTEM\",\n" +
      "      \"active\":true,\n" +
      "      \"authenticationKeys\":{\n" +
      "        \"currentKey\":\"-----BEGIN PUBLIC KEY-----\\nKEY==\\n-----END PUBLIC KEY-----\"\n" +
      "      },\n" +
      "      \"company\":\"application\",\n" +
      "      \"createdBy\":\"13469017454766\",\n" +
      "      \"eTag\":\"1652382873193\",\n" +
      "      \"emailAddress\":\"WHATSAPP.123456789@symphony.com\",\n" +
      "      \"emailAddressNormalized\":\"WHATSAPP.123456789@SYMPHONY.COM\",\n" +
      "      \"entitlement\":[\n" +
      "        {\n" +
      "          \"name\":\"isExternalIMEnabled\",\n" +
      "          \"value\":true\n" +
      "        }\n" +
      "      ],\n" +
      "      \"focusedUser\":false,\n" +
      "      \"id\":13469017534641,\n" +
      "      \"imageUrl\":\"../avatars/static/150/default.png\",\n" +
      "      \"imageUrlSmall\":\"../avatars/static/50/default.png\",\n" +
      "      \"modifiedBy\":\"13469017454766\",\n" +
      "      \"myCurrentThreadId\":\"wE3WU87NU+Uuf3AH5V/fa3///n9GTknddA==\",\n" +
      "      \"preferences\":{\n" +
      "      },\n" +
      "      \"prettyName\":\"FN Add LN Error 1 [WHATSAPP]\",\n" +
      "      \"prettyNameNormalized\":\"FN ADD LN ERROR 1 [WHATSAPP]\",\n" +
      "      \"registrationStatus\":\"REGISTERED\",\n" +
      "      \"roles\":[\n" +
      "        {\n" +
      "          \"id\":\"INDIVIDUAL\"\n" +
      "        }\n" +
      "      ],\n" +
      "      \"samlId\":\"WHATSAPP.123456789\",\n" +
      "      \"screenName\":\"WHATSAPP.123456789\",\n" +
      "      \"source\":\"SERVICE\",\n" +
      "      \"suspended\":false,\n" +
      "      \"userType\":\"system\",\n" +
      "      \"username\":\"WHATSAPP.123456789\",\n" +
      "      \"usernameNormalized\":\"WHATSAPP.123456789\",\n" +
      "      \"verifiedForBadge\":true\n" +
      "    }\n" +
      "  ],\n" +
      "  \"copyDisabled\":false,\n" +
      "  \"event\":\"UPDATE_USER\",\n" +
      "  \"ingestionDate\":1652382873206,\n" +
      "  \"isCopyDisabled\":false,\n" +
      "  \"messageId\":\"KKGL2L3AT5NotZX6LSouO3///n9GTkmJbw==\",\n" +
      "  \"requestingUser\":{\n" +
      "    \"company\":\"Symphony\",\n" +
      "    \"emailAddress\":\"connect@symphony.com\",\n" +
      "    \"id\":1234567890,\n" +
      "    \"imageUrl\":\"../avatars/static/150/default.png\",\n" +
      "    \"imageUrlSmall\":\"../avatars/static/50/default.png\",\n" +
      "    \"prettyName\":\"Symphony Connect Helper\",\n" +
      "    \"userType\":\"system\",\n" +
      "    \"username\":\"connectbot\"\n" +
      "  },\n" +
      "  \"schemaVersion\":1,\n" +
      "  \"semVersion\":\"20.14.1\",\n" +
      "  \"traceId\":\"OcUvs1\",\n" +
      "  \"version\":\"MAESTRO\"\n" +
      "}\n";
  }

  private String getUpdateUserMaestroMessageByUser_Advisor(){
    return "{\n" +
      "  \"_type\":\"com.symphony.s2.model.chat.MaestroMessage\",\n" +
      "  \"affectedUsers\":[\n" +
      "    {\n" +
      "      \"accountType\":\"NORMAL\",\n" +
      "      \"active\":true,\n" +
      "      \"assetClass\":[],\n" +
      "      \"authentication\":{\n" +
      "        \"authGroupId\":\"org_AH0o8ylhP1oxmO8Z\",\n" +
      "        \"method\":\"SSO\"\n" +
      "      },\n" +
      "      \"authenticationKeys\":{\n" +
      "      },\n" +
      "      \"company\":\"Symphony\",\n" +
      "      \"createdBy\":\"15049565405266\",\n" +
      "      \"eTag\":\"1652363484211\",\n" +
      "      \"emailAddress\":\"email@company.com\",\n" +
      "      \"emailAddressNormalized\":\"EMAIL@COMPANY.COM\",\n" +
      "      \"entitlement\":[\n" +
      "        {\n" +
      "          \"name\":\"isExternalIMEnabled\",\n" +
      "          \"value\":true\n" +
      "        }\n" +
      "      ],\n" +
      "      \"firstName\":\"FirstName\",\n" +
      "      \"firstNameNormalized\":\"FIRSTNAME\",\n" +
      "      \"focusedUser\":false,\n" +
      "      \"function\":[],\n" +
      "      \"id\":1234567891011,\n" +
      "      \"imageUrl\":\"../avatars/static/150/default.png\",\n" +
      "      \"imageUrlSmall\":\"../avatars/static/50/default.png\",\n" +
      "      \"industry\":[],\n" +
      "      \"instrument\":[],\n" +
      "      \"lastLoginDate\":1652363322658,\n" +
      "      \"marketCoverage\":[],\n" +
      "      \"modifiedBy\":\"15049565405266\",\n" +
      "      \"myCurrentThreadId\":\"/l3sW0hOcb2bKTyBjHOqcn///n+urr8RdA==\",\n" +
      "      \"prettyName\":\"DisplayName\",\n" +
      "      \"prettyNameNormalized\":\"DISPLAYNAME\",\n" +
      "      \"registrationStatus\":\"REGISTERED\",\n" +
      "      \"responsibility\":[],\n" +
      "      \"roles\":[\n" +
      "        {\n" +
      "          \"id\":\"INDIVIDUAL\"\n" +
      "        }\n" +
      "      ],\n" +
      "      \"samlId\":\"firstName.lastName\",\n" +
      "      \"screenName\":\"firstName.lastName\",\n" +
      "      \"source\":\"ADMIN\",\n" +
      "      \"surname\":\"LastName\",\n" +
      "      \"surnameNormalized\":\"LASTNAME\",\n" +
      "      \"suspended\":false,\n" +
      "      \"userType\":\"lc\",\n" +
      "      \"username\":\"username\",\n" +
      "      \"usernameNormalized\":\"USERNAME\",\n" +
      "      \"verifiedForBadge\":true\n" +
      "    }\n" +
      "  ],\n" +
      "  \"copyDisabled\":false,\n" +
      "  \"event\":\"UPDATE_USER\",\n" +
      "  \"ingestionDate\":1652363484213,\n" +
      "  \"isCopyDisabled\":false,\n" +
      "  \"messageId\":\"qkqbSZt0D5JV24pfYmNkTX///n9HdiPKbw==\",\n" +
      "  \"requestingUser\":{\n" +
      "    \"company\":\"Symphony Support\",\n" +
      "    \"emailAddress\":\"email@symphony.com\",\n" +
      "    \"firstName\":\"firstName\",\n" +
      "    \"id\":15049565405266,\n" +
      "    \"imageUrl\":\"../avatars/static/150/default.png\",\n" +
      "    \"imageUrlSmall\":\"../avatars/static/50/default.png\",\n" +
      "    \"prettyName\":\"prettyName\",\n" +
      "    \"surname\":\"lastName\",\n" +
      "    \"userType\":\"lc\",\n" +
      "    \"username\":\"userName\"\n" +
      "  },\n" +
      "  \"schemaVersion\":1,\n" +
      "  \"semVersion\":\"20.14.30\",\n" +
      "  \"version\":\"MAESTRO\"\n" +
      "}\n";
  }

  private String getUpdateUserMaestroMessageByUser_Bot(){
    return "{\n" +
      "  \"_type\":\"com.symphony.s2.model.chat.MaestroMessage\",\n" +
      "  \"affectedUsers\":[\n" +
      "    {\n" +
      "      \"accountType\":\"SYSTEM\",\n" +
      "      \"active\":true,\n" +
      "      \"assetClass\":[],\n" +
      "      \"authenticationKeys\":{\n" +
      "        \"currentKey\":\"-----BEGIN PUBLIC KEY-----\\nKEY==\\n-----END PUBLIC KEY-----\"\n" +
      "      },\n" +
      "      \"authenticationKeys\":{\n" +
      "      },\n" +
      "      \"company\":\"Symphony\",\n" +
      "      \"createdBy\":\"15049565405266\",\n" +
      "      \"eTag\":\"1652363484211\",\n" +
      "      \"emailAddress\":\"email@company.com\",\n" +
      "      \"emailAddressNormalized\":\"EMAIL@COMPANY.COM\",\n" +
      "      \"entitlement\":[\n" +
      "        {\n" +
      "          \"name\":\"isExternalIMEnabled\",\n" +
      "          \"value\":true\n" +
      "        }\n" +
      "      ],\n" +
      "      \"focusedUser\":false,\n" +
      "      \"id\":1234567891011,\n" +
      "      \"imageUrl\":\"../avatars/static/150/default.png\",\n" +
      "      \"imageUrlSmall\":\"../avatars/static/50/default.png\",\n" +
      "      \"modifiedBy\":\"15049565405266\",\n" +
      "      \"myCurrentThreadId\":\"/l3sW0hOcb2bKTyBjHOqcn///n+urr8RdA==\",\n" +
      "      \"preferences\":{\n" +
      "      },\n" +
      "      \"prettyName\":\"DisplayName\",\n" +
      "      \"prettyNameNormalized\":\"DISPLAYNAME\",\n" +
      "      \"registrationStatus\":\"REGISTERED\",\n" +
      "      \"roles\":[\n" +
      "        {\n" +
      "          \"id\":\"INDIVIDUAL\"\n" +
      "        }\n" +
      "      ],\n" +
      "      \"samlId\":\"jiggly.trader\",\n" +
      "      \"screenName\":\"jiggly.trader\",\n" +
      "      \"source\":\"SERVICE\",\n" +
      "      \"suspended\":false,\n" +
      "      \"userType\":\"system\",\n" +
      "      \"username\":\"username\",\n" +
      "      \"usernameNormalized\":\"USERNAME\",\n" +
      "      \"verifiedForBadge\":true\n" +
      "    }\n" +
      "  ],\n" +
      "  \"copyDisabled\":false,\n" +
      "  \"event\":\"UPDATE_USER\",\n" +
      "  \"ingestionDate\":1652363484213,\n" +
      "  \"isCopyDisabled\":false,\n" +
      "  \"messageId\":\"qkqbSZt0D5JV24pfYmNkTX///n9HdiPKbw==\",\n" +
      "  \"requestingUser\":{\n" +
      "    \"company\":\"Symphony Support\",\n" +
      "    \"emailAddress\":\"email@symphony.com\",\n" +
      "    \"firstName\":\"firstName\",\n" +
      "    \"id\":15049565405266,\n" +
      "    \"imageUrl\":\"../avatars/static/150/default.png\",\n" +
      "    \"imageUrlSmall\":\"../avatars/static/50/default.png\",\n" +
      "    \"prettyName\":\"prettyName\",\n" +
      "    \"surname\":\"lastName\",\n" +
      "    \"userType\":\"lc\",\n" +
      "    \"username\":\"userName\"\n" +
      "  },\n" +
      "  \"schemaVersion\":1,\n" +
      "  \"semVersion\":\"20.14.30\",\n" +
      "  \"version\":\"MAESTRO\"\n" +
      "}\n";
  }

  private String getCreateMIMMaestroMessage(FederatedAccount whatsAppUserInvited, UserInfo symphonyUserInvited, UserInfo inviter) {
    return " {" +
      "  \"_type\":\"com.symphony.s2.model.chat.MaestroMessage\"," +
      "  \"affectedUsers\":[" +
      "    {" +
      "      \"id\":" + inviter.getId() + "," +
      "      \"principalBaseHash\":\"5ypkFskUVGcFrg4oTp/FxAduAhod13JmP4c5canP1bQBAQ==\"" +
      "    }," +
      "    {" +
      "      \"id\":" + symphonyUserInvited.getId() + "," +
      "      \"principalBaseHash\":\"9pS2Ip6ti+aRsmm7BJee6JPE7FSGN1nH4FqJDoDj6HoBAQ==\"" +
      "    }," +
      "    {" +
      "      \"id\":" + whatsAppUserInvited.getSymphonyUserId() + "," +
      "      \"principalBaseHash\":\"1XEPBZKPz3gY+sVyn1QiEoWCfYr/RUATByAtBuqzkiwBAQ==\"" +
      "    }" +
      "  ]," +
      "  \"copyDisabled\":true," +
      "  \"event\":\"CREATE_IM\"," +
      "  \"fromPod\":196," +
      "  \"ingestionDate\":1571736690787," +
      "  \"isCopyDisabled\":true," +
      "  \"maestroObject\":{" +
      "    \"active\":true," +
      "    \"allowExternal\":false," +
      "    \"cepPodIds\":[]," +
      "    \"copyDisabled\":false," +
      "    \"creationDate\":1571736690751," +
      "    \"creatorId\":" + inviter.getId() + "," +
      "    \"crossPod\":false," +
      "    \"cryptoRotationInfo\":{" +
      "      \"acceptedRotationId\":0," +
      "      \"acceptedRotationRange\":1," +
      "      \"retiredRotationId\":-1" +
      "    }," +
      "    \"discoverable\":false," +
      "    \"externalOwned\":false," +
      "    \"forDisplay\":false," +
      "    \"id\":\"5daecc7243070707d15c69ff\"," +
      "    \"largeStream\":false," +
      "    \"lastMessageTimeStamp\":0," +
      "    \"lastStatefulMid\":\"36qr/jjc9PFwIkv65MvBlH///pINMV+/ZA==\"," +
      "    \"locked\":false," +
      "    \"memberAddUserEnabled\":false," +
      "    \"moderated\":false," +
      "    \"multiLateral\":false," +
      "    \"podDistributionList\":[" +
      "      196" +
      "    ]," +
      "    \"publicRoom\":false," +
      "    \"readOnly\":false," +
      "    \"restrictedDistributionList\":false," +
      "    \"sendMessageDisabled\":false," +
      "    \"shareHistoryEnabled\":false," +
      "    \"state\":\"CREATED\"," +
      "    \"streamId\":\"KdO82B8UMTU7og2M4vOFqn///pINMV/OdA==\"," +
      "    \"type\":\"MIM\"," +
      "    \"typingEventEnabled\":true," +
      "    \"userIdKey\":\"" + inviter.getId() + "," + symphonyUserInvited.getId() + "," + whatsAppUserInvited.getSymphonyUserId() + "\"" +
      "  }," +
      "  \"messageId\":\"KGH8rC8fU5oPP8xdRarlLX///pINMV+cbw==\"," +
      "  \"payload\":{" +
      "    \"cargo\":{" +
      "      \"affectedUserId\":" + whatsAppUserInvited.getSymphonyUserId() + "," +
      "      \"initiatorUserId\":" + inviter.getId() + "," +
      "      \"roomType\":\"MULTI_PARTY_IM\"" +
      "    }," +
      "    \"version\":\"createIMPayload\"" +
      "  }," +
      "  \"podDistribution\":[" +
      "    196" +
      "  ]," +
      "  \"requestingUser\":{" +
      "    \"company\":\"Symphony\"," +
      "    \"emailAddress\":\"" + inviter.getEmailAddress() + "\"," +
      "    \"firstName\":\"" + inviter.getFirstName() + "\"," +
      "    \"id\":" + inviter.getId() + "," +
      "    \"imageUrl\":\"../avatars/static/150/default.png\"," +
      "    \"imageUrlSmall\":\"../avatars/static/50/default.png\"," +
      "    \"prettyName\":\"" + inviter.getFirstName() + " " + inviter.getLastName() + "\"," +
      "    \"principalBaseHash\":\"5ypkFskUVGcFrg4oTp/FxAduAhod13JmP4c5canP1bQBAQ==\"," +
      "    \"surname\":\"" + inviter.getLastName() + "\"," +
      "    \"userType\":\"lc\"," +
      "    \"username\":\"" + inviter.getFirstName() + "\"" +
      "  }," +
      "  \"schemaVersion\":1," +
      "  \"semVersion\":\"1.55.3-SNAPSHOT\"," +
      "  \"threadId\":\"KdO82B8UMTU7og2M4vOFqn///pINMV/OdA==\"," +
      "  \"traceId\":\"OOMErh\"," +
      "  \"version\":\"MAESTRO\"" +
      "}";
  }

  private String getCreateIMMaestroMessage(FederatedAccount whatsAppUserInvited, UserInfo inviter) {
    return " {" +
      "  \"_type\":\"com.symphony.s2.model.chat.MaestroMessage\"," +
      "  \"affectedUsers\":[" +
      "    {" +
      "      \"id\":" + inviter.getId() + "," +
      "      \"principalBaseHash\":\"5ypkFskUVGcFrg4oTp/FxAduAhod13JmP4c5canP1bQBAQ==\"" +
      "    }," +
      "    {" +
      "      \"id\":" + whatsAppUserInvited.getSymphonyUserId() + "," +
      "      \"principalBaseHash\":\"1XEPBZKPz3gY+sVyn1QiEoWCfYr/RUATByAtBuqzkiwBAQ==\"" +
      "    }" +
      "  ]," +
      "  \"copyDisabled\":true," +
      "  \"event\":\"CREATE_IM\"," +
      "  \"fromPod\":196," +
      "  \"ingestionDate\":1571736690787," +
      "  \"isCopyDisabled\":true," +
      "  \"maestroObject\":{" +
      "    \"active\":true," +
      "    \"allowExternal\":false," +
      "    \"cepPodIds\":[]," +
      "    \"copyDisabled\":false," +
      "    \"creationDate\":1571736690751," +
      "    \"creatorId\":" + inviter.getId() + "," +
      "    \"crossPod\":false," +
      "    \"cryptoRotationInfo\":{" +
      "      \"acceptedRotationId\":0," +
      "      \"acceptedRotationRange\":1," +
      "      \"retiredRotationId\":-1" +
      "    }," +
      "    \"discoverable\":false," +
      "    \"externalOwned\":false," +
      "    \"forDisplay\":false," +
      "    \"id\":\"5daecc7243070707d15c69ff\"," +
      "    \"largeStream\":false," +
      "    \"lastMessageTimeStamp\":0," +
      "    \"lastStatefulMid\":\"36qr/jjc9PFwIkv65MvBlH///pINMV+/ZA==\"," +
      "    \"locked\":false," +
      "    \"memberAddUserEnabled\":false," +
      "    \"moderated\":false," +
      "    \"multiLateral\":false," +
      "    \"podDistributionList\":[" +
      "      196" +
      "    ]," +
      "    \"publicRoom\":false," +
      "    \"readOnly\":false," +
      "    \"restrictedDistributionList\":false," +
      "    \"sendMessageDisabled\":false," +
      "    \"shareHistoryEnabled\":false," +
      "    \"state\":\"CREATED\"," +
      "    \"streamId\":\"KdO82B8UMTU7og2M4vOFqn///pINMV/OdA==\"," +
      "    \"type\":\"MIM\"," +
      "    \"typingEventEnabled\":true," +
      "    \"userIdKey\":\"" + inviter.getId() + "," + whatsAppUserInvited.getSymphonyUserId() + "\"" +
      "  }," +
      "  \"messageId\":\"KGH8rC8fU5oPP8xdRarlLX///pINMV+cbw==\"," +
      "  \"payload\":{" +
      "    \"cargo\":{" +
      "      \"affectedUserId\":" + whatsAppUserInvited.getSymphonyUserId() + "," +
      "      \"initiatorUserId\":" + inviter.getId() + "," +
      "      \"roomType\":\"MULTI_PARTY_IM\"" +
      "    }," +
      "    \"version\":\"createIMPayload\"" +
      "  }," +
      "  \"podDistribution\":[" +
      "    196" +
      "  ]," +
      "  \"requestingUser\":{" +
      "    \"company\":\"Symphony\"," +
      "    \"emailAddress\":\"" + inviter.getEmailAddress() + "\"," +
      "    \"firstName\":\"" + inviter.getFirstName() + "\"," +
      "    \"id\":" + inviter.getId() + "," +
      "    \"imageUrl\":\"../avatars/static/150/default.png\"," +
      "    \"imageUrlSmall\":\"../avatars/static/50/default.png\"," +
      "    \"prettyName\":\"" + inviter.getFirstName() + " " + inviter.getLastName() + "\"," +
      "    \"principalBaseHash\":\"5ypkFskUVGcFrg4oTp/FxAduAhod13JmP4c5canP1bQBAQ==\"," +
      "    \"surname\":\"" + inviter.getLastName() + "\"," +
      "    \"userType\":\"lc\"," +
      "    \"username\":\"" + inviter.getFirstName() + "\"" +
      "  }," +
      "  \"schemaVersion\":1," +
      "  \"semVersion\":\"1.55.3-SNAPSHOT\"," +
      "  \"threadId\":\"KdO82B8UMTU7og2M4vOFqn///pINMV/OdA==\"," +
      "  \"traceId\":\"OOMErh\"," +
      "  \"version\":\"MAESTRO\"" +
      "}";
  }

  private String getCreateRoomMaestroMessage(FederatedAccount whatsAppUserInvited, UserInfo inviter) {
    return "{" +
      "  \"_type\":\"com.symphony.s2.model.chat.MaestroMessage\"," +
      "  \"affectedUsers\":[" +
      "    {" +
      "      \"company\":\"Symphony\"," +
      "      \"emailAddress\":\"" + inviter.getEmailAddress() + "\"," +
      "      \"firstName\":\"" + inviter.getFirstName() + "\"," +
      "      \"id\":" + inviter.getId() + "," +
      "      \"imageUrl\":\"../avatars/static/150/default.png\"," +
      "      \"imageUrlSmall\":\"../avatars/static/50/default.png\"," +
      "      \"prettyName\":\"" + inviter.getFirstName() + " " + inviter.getLastName() + "\"," +
      "      \"principalBaseHash\":\"5ypkFskUVGcFrg4oTp/FxAduAhod13JmP4c5canP1bQBAQ==\"," +
      "      \"surname\":\"" + inviter.getLastName() + "\"," +
      "      \"userType\":\"lc\"," +
      "      \"username\":\"" + inviter.getFirstName() + "\"" +
      "    }," +
      "    {" +
      "      \"company\":\"Symphony\"," +
      "      \"emailAddress\":\"" + UUID.randomUUID() + "@symphony.com" + "\"," +
      "      \"firstName\":\"" + whatsAppUserInvited.getFirstName() + "\"," +
      "      \"id\":" + whatsAppUserInvited.getSymphonyUserId() + "," +
      "      \"imageUrl\":\"../avatars/static/150/default.png\"," +
      "      \"imageUrlSmall\":\"../avatars/static/50/default.png\"," +
      "      \"prettyName\":\"" + whatsAppUserInvited.getFirstName() + " " + whatsAppUserInvited.getLastName() + "\"," +
      "      \"principalBaseHash\":\"5ypkFskUVGcFrg4oTp/FxAduAhod13JmP4c5canP1bQBAQ==\"," +
      "      \"surname\":\"" + whatsAppUserInvited.getLastName() + "\"," +
      "      \"userType\":\"lc\"," +
      "      \"username\":\"" + whatsAppUserInvited.getFirstName() + "\"" +
      "    }" +
      "  ]," +
      "  \"copyDisabled\":false," +
      "  \"event\":\"JOIN_ROOM\"," +
      "  \"fromPod\":196," +
      "  \"ingestionDate\":1571397117955," +
      "  \"isCopyDisabled\":false," +
      "  \"maestroObject\":{" +
      "    \"active\":true," +
      "    \"allowExternal\":false," +
      "    \"copyDisabled\":false," +
      "    \"creationDate\":1571397117636," +
      "    \"creator\":" + inviter.getId() + "," +
      "    \"creatorPrettyName\":\"" + inviter.getFirstName() + " " + inviter.getLastName() + "\"," +
      "    \"crossPodRoom\":false," +
      "    \"crossPodUser\":false," +
      "    \"cryptoRotationInfo\":{" +
      "      \"acceptedRotationId\":0," +
      "      \"acceptedRotationRange\":1," +
      "      \"retiredRotationId\":-1" +
      "    }," +
      "    \"description\":\"Test Room 1\"," +
      "    \"discoverable\":false," +
      "    \"distributionLists\":[" +
      "      196" +
      "    ]," +
      "    \"forDisplay\":false," +
      "    \"futureDeactivationDate\":-1," +
      "    \"hidden\":false," +
      "    \"imageUrl\":\"../avatars/static/150/default.png\"," +
      "    \"imageUrlSmall\":\"../avatars/static/50/default.png\"," +
      "    \"largeStream\":false," +
      "    \"lastDisabled\":-1," +
      "    \"lockDate\":-1," +
      "    \"locked\":false," +
      "    \"streamId\":\"KdO82B8UMTU7og2M4vOFqn///pINMV/OdA==\"," +
      "    \"memberAddUserEnabled\":false," +
      "    \"memberCount\":1," +
      "    \"membership\":[" +
      "      {" +
      "        \"company\":\"Symphony\"," +
      "        \"crossPodUser\":false," +
      "        \"hidden\":false," +
      "        \"imageUrl\":\"../avatars/static/150/default.png\"," +
      "        \"imageUrlSmall\":\"../avatars/static/50/default.png\"," +
      "        \"images\":{" +
      "        }," +
      "        \"joinDate\":1571397117917," +
      "        \"owner\":true," +
      "        \"podId\":0," +
      "        \"prettyName\":\"" + inviter.getFirstName() + " " + inviter.getLastName() + "\"," +
      "        \"screenName\":\"" + inviter.getFirstName() + "\"," +
      "        \"userId\":" + inviter.getId() + "" +
      "      }," +
      "      {" +
      "        \"company\":\"Symphony\"," +
      "        \"crossPodUser\":false," +
      "        \"hidden\":false," +
      "        \"imageUrl\":\"../avatars/static/150/default.png\"," +
      "        \"imageUrlSmall\":\"../avatars/static/50/default.png\"," +
      "        \"images\":{" +
      "        }," +
      "        \"joinDate\":1571397117917," +
      "        \"owner\":true," +
      "        \"podId\":0," +
      "        \"prettyName\":\"" + whatsAppUserInvited.getFirstName() + " " + whatsAppUserInvited.getLastName() + "\"," +
      "        \"screenName\":\"" + whatsAppUserInvited.getFirstName() + "\"," +
      "        \"userId\":" + whatsAppUserInvited.getSymphonyUserId() + "" +
      "      }" +
      "    ]," +
      "    \"moderated\":false," +
      "    \"multiLateralRoom\":false," +
      "    \"name\":\"Test Room 1\"," +
      "    \"nameKey\":\"testroom1\"," +
      "    \"podDistributionHistory\":[" +
      "      196" +
      "    ]," +
      "    \"podDistributionList\":[" +
      "      196" +
      "    ]," +
      "    \"podRestrictionList\":[]," +
      "    \"publicRoom\":false," +
      "    \"readOnly\":false," +
      "    \"removalDateAfterLastMessage\":1573989117635," +
      "    \"requested\":false," +
      "    \"restrictedDistributionList\":false," +
      "    \"roomType\":\"CHATROOM\"," +
      "    \"senderAnonymous\":false," +
      "    \"shareHistoryEnabled\":true," +
      "    \"threadId\":\"KdO82B8UMTU7og2M4vOFqn///pINMV/OdA==\"," +
      "    \"typingEventEnabled\":true," +
      "    \"userId\":" + inviter.getId() + "," +
      "    \"userIsOwner\":true," +
      "    \"userJoinDate\":1571397117917," +
      "    \"userPrettyName\":\"" + inviter.getFirstName() + " " + inviter.getLastName() + "\"" +
      "  }," +
      "  \"messageId\":\"ya1OqSkI4DCq7XL3AdQVKn///pIhbtf8bw==\"," +
      "  \"payload\":{" +
      "    \"cargo\":{" +
      "      \"copyDisabled\":false," +
      "      \"creationDate\":1571397117636," +
      "      \"creator\":" + inviter.getId() + "," +
      "      \"cryptoRotationInfo\":{" +
      "        \"acceptedRotationId\":0," +
      "        \"acceptedRotationRange\":1," +
      "        \"retiredRotationId\":-1" +
      "      }," +
      "      \"description\":\"Test Room 1\"," +
      "      \"discoverable\":false," +
      "      \"initiatorUserId\":" + inviter.getId() + "," +
      "      \"isRoomPublic\":false," +
      "      \"memberAddUserEnabled\":false," +
      "      \"name\":\"Test Room 1\"," +
      "      \"podDistributionHistory\":[" +
      "        196" +
      "      ]," +
      "      \"podRestrictionList\":[]," +
      "      \"readOnly\":false," +
      "      \"shareHistoryEnabled\":true" +
      "    }," +
      "    \"version\":\"createRoomPayload\"" +
      "  }," +
      "  \"podDistribution\":[" +
      "    196" +
      "  ]," +
      "  \"requestingUser\":{" +
      "    \"company\":\"Symphony\"," +
      "    \"emailAddress\":\"" + inviter.getEmailAddress() + "\"," +
      "    \"firstName\":\"" + inviter.getFirstName() + "\"," +
      "    \"id\":" + inviter.getId() + "," +
      "    \"imageUrl\":\"../avatars/static/150/default.png\"," +
      "    \"imageUrlSmall\":\"../avatars/static/50/default.png\"," +
      "    \"prettyName\":\"" + inviter.getFirstName() + " " + inviter.getLastName() + "\"," +
      "    \"principalBaseHash\":\"5ypkFskUVGcFrg4oTp/FxAduAhod13JmP4c5canP1bQBAQ==\"," +
      "    \"surname\":\"" + inviter.getLastName() + "\"," +
      "    \"userType\":\"lc\"," +
      "    \"username\":\"" + inviter.getFirstName() + "\"" +
      "  }," +
      "  \"schemaVersion\":1," +
      "  \"semVersion\":\"1.55.3-SNAPSHOT\"," +
      "  \"threadId\":\"KdO82B8UMTU7og2M4vOFqn///pINMV/OdA==\"," +
      "  \"traceId\":\"yRu0c3\"," +
      "  \"version\":\"MAESTRO\"" +
      "}";
  }

  private String getRoomMessageSocialMessage(FederatedAccount whatsAppUser, UserInfo... otherParticipants) {
    return getMessageSocialMessage(whatsAppUser, Arrays.asList(otherParticipants), "CHATROOM");
  }

  private String getMIMMessageSocialMessage(FederatedAccount whatsAppUser, UserInfo... otherParticipants) {
    return getMessageSocialMessage(whatsAppUser, Arrays.asList(otherParticipants), "INSTANT_CHAT");
  }

  private String getMessageSocialMessage(FederatedAccount whatsAppUser, List<UserInfo> participants, String chatType) {
    return "{" +
      "  \"_type\":\"com.symphony.s2.model.chat.SocialMessage\"," +
      "  \"attributes\":{" +
      "    \"dist\":[" +
      "      " + whatsAppUser.getSymphonyUserId() + "," +
      participants.stream().map(UserInfo::getId).map(Object::toString).collect(Collectors.joining(",")) +
      "    ]" +
      "  }," +
      "  \"chatType\":\""+chatType+"\"," +
      "  \"clientId\":\"web-1530171158851-1719\"," +
      "  \"clientVersionInfo\":\"DESKTOP-36.0.0-6541-MacOSX-10.12.6-Chrome-67.0.3396.99\"," +
      "  \"copyDisabled\":false," +
      "  \"encrypted\":true," +
      "  \"attachments\":[" +
      "    { " +
      "      \"blocked\":false," +
      "      \"contentType\":\"image/png\"," +
      "      \"encrypted\":true," +
      "      \"fileId\":\"external_13606456399657%2Faa6RH%2FfBZigZVymvXYAu3A%3D%3D\"," +
      "      \"height\":192," +
      "      \"images\":{" +
      "        \"600\":\"external_13606456399657%2FAafcE4EheP1Pi0%2Fq1UwhAQ%3D%3D\"" +
      "      }," +
      "    \"malwareScanRequired\":false," +
      "    \"name\":\"Symphony.png\"," +
      "    \"sizeInBytes\":5533," +
      "    \"width\":192" +
      "    }" +
      "  ]," +
      "  \"encryptedEntities\":\"AgAAAKcAAAAAAAAAAJ6O1SMRXgf68fdt6/pZaSHQvxIWpgq2hbHDhAiuAbnQow8SgdSrv4WAFYyUIHqT3EFBYK6AC4ZG/YfxEAVM9IJfKZ5pU5dXxoTjxU5VmpASlXY=\"," +
      "  \"encryptedMedia\":\"AgAAAKcAAAAAAAAAAP5fS7NnNyx1o+gq5kzQQ2XshB3EF3nTsDxvM2uD6yMebqWOnRdUSRCbz/dfMQ0UBVHpYBy7CxuXJm6g3bhJ2glBdl3VO9Kmn+gzn7EpJ1todA==\"," +
      "  \"enforceExpressionFiltering\":true," +
      "  \"entities\":{" +
      "  }," +
      "  \"externalOrigination\":false," +
      "  \"fileKeyEncryptedAttachments\":[" +
      "    { " +
      "      \"blocked\":false," +
      "      \"contentType\":\"image/png\"," +
      "      \"encrypted\":true," +
      "      \"fileId\":\"external_13606456399657%2FQ5w0LrJ%2F%2FwGDDwJqMLO5bQ%3D%3D\"," +
      "      \"height\":0," +
      "      \"images\":{" +
      "        \"600\":\"\"" +
      "      }," +
      "    \"malwareScanRequired\":false," +
      "    \"malwareScanState\":{" +
      "      \"details\":{" +
      "        \"link\":{" +
      "          \"text\":\"\"," +
      "          \"url\":\"\"" +
      "        }," +
      "        \"message\":\"\"" +
      "      }," +
      "      \"status\":\"\"" +
      "    }," +
      "    \"name\":\"Symphony2.png\"," +
      "    \"sizeInBytes\":5566," +
      "    \"width\":0" +
      "    }" +
      "  ]," +
      "  \"format\":\"com.symphony.markdown\"," +
      "  \"from\":{" +
      "    \"emailAddress\":\"" + participants.get(0).getEmailAddress() + "\"," +
      "    \"firstName\":\"" + participants.get(0).getFirstName() + "\"," +
      "    \"id\":" + participants.get(0).getId() + "," +
      "    \"imageUrl\":\"https://s3.amazonaws.com/user-pics-demo/small/symphony_small.png\"," +
      "    \"imageUrlSmall\":\"https://s3.amazonaws.com/user-pics-demo/creepy/symphony_creepy.png\"," +
      "    \"prettyName\":\"" + participants.get(0).getFirstName() + " " + participants.get(0).getLastName() + "\"," +
      "    \"principalBaseHash\":\"dUTW4YYIa6vxg0_zzD-J8Rbj2P2Jsevlc5PRvSwBHx4BAQ\"," +
      "    \"surname\":\"" + participants.get(0).getLastName() + "\"," +
      "    \"userType\":\"lc\"," +
      "    \"username\":\"" + participants.get(0).getEmailAddress() + "\"," +
      "    \"company\":\"symphony\"" +
      "  }," +
      "  \"fromPod\":196," +
      "  \"ignoreDLPWarning\":false," +
      "  \"ingestionDate\":1530188333754," +
      "  \"isBlast\":false," +
      "  \"isChime\":false," +
      "  \"isCopyDisabled\":false," +
      "  \"isImported\":false," +
      "  \"isPrivate\":true," +
      "  \"lastState\":\"5P5CNZPAhTXCBXTPJEZttn///pwsItaTbw==\"," +
      "  \"messageId\":\"GUrHhnXNMHd9OBIJzyciiX///pu5qv1FbQ==\"," +
      "  \"msgFeatures\":\"7\"," +
      "  \"objectHash\":\"/OjFS3px1llj5muYEzpFgd0DDDEuiRNjmK2SZb3f7xcBAQ==\"," +
      "  \"platform\":\"DESKTOP\"," +
      "  \"podDistribution\":[" +
      "    196" +
      "  ]," +
      "  \"reloadPolicies\":false," +
      "  \"schemaVersion\":1," +
      "  \"semVersion\":\"1.51.0\"," +
      "  \"sendingApp\":\"lc\"," +
      "  \"suppressed\":false," +
      "  \"text\":\"AgAAAKcAAAAAAAAAAJBTMg39Qo3WEfLEe+AqSjW7o44NYr5RY0X0q2jk1+13mL5CNQZs5gNdrJv8gv54W88=\"," +
      "  \"threadId\":\"KdO82B8UMTU7og2M4vOFqn///pINMV/OdA==\"," +
      "  \"tokenIds\":[" +
      "    \"Ggi5HWXF/7Ao+XP7TW18NNxtAGZx21M1YYM18QFxZuM=\"" +
      "  ]," +
      "  \"traceId\":\"8YbDZU\"," +
      "  \"uniqueMessageId\":0," +
      "  \"version\":\"SOCIALMESSAGE\"" +
      "}";
  }

  private String getSnsMaestroMessage(String podId, String payload) {
    return "{" +
      "  \"Message\":\"{\\\"payload\\\":\\\"" + Base64.encodeBase64String(payload.getBytes()) + "\\\"}\"," +
      "  \"MessageAttributes\":{" +
      "    \"payloadType\":{" +
      "      \"Type\":\"String\"," +
      "      \"Value\":\"com.symphony.s2.model.chat.MaestroMessage\"" +
      "    }," +
      "    \"podId\":{" +
      "      \"Type\":\"Number\"," +
      "      \"Value\":\"" + podId + "\"" +
      "    }" +
      "  }," +
      "  \"Type\":\"Notification\"" +
      "}";
  }

  private String getSnsSocialMessage(String podId, String payload) {
    return "{" +
      "  \"Message\":\"{\\\"payload\\\":\\\"" + Base64.encodeBase64String(payload.getBytes()) + "\\\"}\"," +
      "  \"MessageAttributes\":{" +
      "    \"payloadType\":{" +
      "      \"Type\":\"String\"," +
      "      \"Value\":\"com.symphony.s2.model.chat.SocialMessage\"" +
      "    }," +
      "    \"podId\":{" +
      "      \"Type\":\"Number\"," +
      "      \"Value\":\"" + podId + "\"" +
      "    }" +
      "  }," +
      "  \"Type\":\"Notification\"" +
      "}";
  }

  private String getEnvelopeMessage(String payload, MaestroEventType type, String podId) {
    return "{" +
      "  \"_type\":\"com.symphony.s2.model.core.Envelope\"," +
      "  \"_version\":\"1.0\"," +
      "  \"createdDate\":\"2020-03-17T12:39:59.117Z\"," +
      "  \"distributionList\":[" +
      "    13469017440257" +
      "  ]," +
      "  \"notificationDate\":\"2020-03-17T12:39:59.407Z\"," +
      "  \"payload\":" + payload + "," +
      "  \"payloadType\":\"com.symphony.s2.model.chat.MaestroMessage." + type + " \"," +
      "  \"podId\":" + podId + "," +
      "  \"purgeDate\":\"2027-03-16T12:39:59.117Z\"" +
      "}";
  }

  private String getEnvelopeSocialMessage(String payload) {
    return "{" +
      "  \"_type\":\"com.symphony.s2.model.core.Envelope\"," +
      "  \"_version\":\"1.0\"," +
      "  \"createdDate\":\"2020-03-17T12:39:59.117Z\"," +
      "  \"distributionList\":[" +
      "    13469017440257" +
      "  ]," +
      "  \"notificationDate\":\"2020-03-17T12:39:59.407Z\"," +
      "  \"payload\":" + payload + "," +
      "  \"payloadType\":\"com.symphony.s2.model.chat.SocialMessage\"," +
      "  \"podId\":196," +
      "  \"purgeDate\":\"2027-03-16T12:39:59.117Z\"" +
      "}";
  }

  private static GatewaySocialMessage generateGatewaySocialMessage(IUser iUser, List<IAttachment> iAttachments, List<String> members, List<String> toUserIds, String chatType) {
    return GatewaySocialMessage.builder()
      .streamId("KdO82B8UMTU7og2M4vOFqn___pINMV_OdA")
      .messageId("GUrHhnXNMHd9OBIJzyciiX___pu5qv1FbQ")
      .textContent("message decrypted")
      .fromUser(iUser)
      .members(members)
      .timestamp(1530188333754L)
      .chime(false)
      .attachments(iAttachments)
      .chatType(chatType)
      .parentRelationshipType(ParentRelationshipType.NONE)
      .toUserIds(toUserIds)
      .build();
  }
}
