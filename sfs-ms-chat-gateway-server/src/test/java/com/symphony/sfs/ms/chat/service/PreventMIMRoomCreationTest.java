package com.symphony.sfs.ms.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.symphony.sfs.ms.admin.generated.model.CanChatResponse;
import com.symphony.sfs.ms.admin.generated.model.EmpList;
import com.symphony.sfs.ms.chat.api.util.AbstractIntegrationTest;
import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.datafeed.GatewaySocialMessage;
import com.symphony.sfs.ms.chat.datafeed.MessageDecryptor;
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
import com.symphony.sfs.ms.starter.security.StaticSessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.util.RsaUtils;
import model.UserInfo;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.symphony.sfs.ms.starter.testing.MockitoUtils.once;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PreventMIMRoomCreationTest extends AbstractIntegrationTest {

  protected ForwarderQueueConsumer forwarderQueueConsumer;
  private SymphonyMessageSender symphonyMessageSender;
  private MockEmpClient empClient;
  private DatafeedSessionPool datafeedSessionPool;
  private SymphonyService symphonyService;
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
    podConfiguration.setUrl("podUrl");
    podConfiguration.setSessionAuth("sessionAuth");
    podConfiguration.setKeyAuth("keyAuth");

    SymphonySession userSession = new SymphonySession("username", "kmToken", "sessionToken");
    when(authenticationService.authenticate(anyString(), anyString(), anyString(), anyString())).thenReturn(userSession);


    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair keyPair = kpg.generateKeyPair();

    ChatConfiguration chatConfiguration = new ChatConfiguration();
    chatConfiguration.setSharedPrivateKey(new PemResource(RsaUtils.encodeRSAKey(keyPair.getPrivate())));
    chatConfiguration.setSharedPublicKey(new PemResource(RsaUtils.encodeRSAKey(keyPair.getPublic())));

    adminClient = mock(DefaultAdminClient.class);
    FederatedAccountSessionService federatedAccountSessionService = new FederatedAccountSessionService(federatedAccountRepository);

    datafeedSessionPool = new DatafeedSessionPool(authenticationService, podConfiguration, chatConfiguration, federatedAccountSessionService, meterManager);
    ObjectMapper objectMapper = new JacksonConfiguration().configureJackson(new ObjectMapper());

    messageDecryptor = mock(MessageDecryptor.class);
    forwarderQueueConsumer = new ForwarderQueueConsumer(objectMapper, messageDecryptor, datafeedSessionPool, new MessageIOMonitor(meterManager), meterManager, botConfiguration);

    when(adminClient.getEmpList()).thenReturn(new EmpList());
    EmpSchemaService empSchemaService = new EmpSchemaService(adminClient);

    SymphonyMessageService messageService = new SymphonyMessageService(empClient, federatedAccountRepository, forwarderQueueConsumer, datafeedSessionPool, symphonyMessageSender, adminClient, empSchemaService, symphonyService, podConfiguration, botConfiguration, authenticationService, null, streamService, new MessageIOMonitor(meterManager), channelService, messageSource);
    messageService.registerAsDatafeedListener();

    ChannelService channelService = new ChannelService(streamService, symphonyMessageSender, podConfiguration, empClient, forwarderQueueConsumer, datafeedSessionPool, federatedAccountRepository, adminClient, empSchemaService, symphonyService, channelRepository, authenticationService, botConfiguration, messageSource);
    channelService.registerAsDatafeedListener();
  }

  @Test
  public void createMIMChannel() throws Exception {
    FederatedAccount whatsAppUserInvited = FederatedAccount.builder()
      .emailAddress("emailAddress@symphony.com")
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
    )));

    forwarderQueueConsumer.consume(notification, "1");
    assertEquals(0, empClient.getChannels().size());
//    verify(symphonyMessageSender, times(1)).sendAlertMessage(session, "KdO82B8UMTU7og2M4vOFqn___pINMV_OdA", "You are not allowed to invite a WHATSAPP contact in a MIM.");
  }

  @Test
  public void createIMChannel() throws Exception {
    FederatedAccount whatsAppUserInvited = FederatedAccount.builder()
      .emailAddress("emailAddress@symphony.com")
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
    )));

    forwarderQueueConsumer.consume(notification, "1");

//    assertEquals(0, ((MockEmpClient) empClient).getChannels().size());
//    verify(symphonyMessageSender, times(0)).sendAlertMessage(session, "KdO82B8UMTU7og2M4vOFqn___pINMV_OdA", "You are not allowed to invite WHATSAPP contacts in a MIM.");

  }

  @Test
  public void onUserJoinedRoom() throws Exception {
    FederatedAccount whatsAppUserInvited = FederatedAccount.builder()
      .emailAddress("emailAddress@symphony.com")
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

    SymphonySession session = datafeedSessionPool.listenDatafeed(whatsAppUserInvited);
    federatedAccountRepository.save(whatsAppUserInvited);
    doNothing().when(symphonyService).removeMemberFromRoom("KdO82B8UMTU7og2M4vOFqn///pINMV/OdA==", session);

    String notification = getSnsMaestroMessage("196", getEnvelopeMessage(getCreateRoomMaestroMessage(
      whatsAppUserInvited,
      inviter
    )));

    forwarderQueueConsumer.consume(notification, "1");

//    verify(symphonyService, times(1)).removeMemberFromRoom("KdO82B8UMTU7og2M4vOFqn___pINMV_OdA", session);
//    verify(symphonyMessageSender, times(1)).sendAlertMessage(session, "KdO82B8UMTU7og2M4vOFqn___pINMV_OdA", "You are not allowed to invite a WHATSAPP contact in a chat room.");
  }

  @Test
  public void onIMMessageNotEntitled() throws Exception {
    FederatedAccount whatsAppUser = FederatedAccount.builder()
      .emailAddress("emailAddress@symphony.com")
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

    SymphonySession session = datafeedSessionPool.listenDatafeed(whatsAppUser);
    federatedAccountRepository.save(whatsAppUser);

    String notification = getSnsSocialMessage("196", getEnvelopeMessage(getMIMMessageMaestroMessage(
      whatsAppUser,
      sender
    )));
    doNothing().when(messageDecryptor).decrypt(any(), eq(whatsAppUser.getSymphonyUserId()), any());
    forwarderQueueConsumer.consume(notification, "1");

    verify(symphonyMessageSender, once()).sendAlertMessage(eq(session), eq("KdO82B8UMTU7og2M4vOFqn___pINMV_OdA"), eq("You are not permitted to send messages to WHATSAPP users."));
  }

  @Test
  public void onIMMessage() throws Exception {
    FederatedAccount whatsAppUser = FederatedAccount.builder()
      .emailAddress("emailAddress@symphony.com")
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

    SymphonySession session = datafeedSessionPool.listenDatafeed(whatsAppUser);
    federatedAccountRepository.save(whatsAppUser);

    String notification = getSnsSocialMessage("196", getEnvelopeMessage(getMIMMessageMaestroMessage(
      whatsAppUser,
      sender
    )));

    doAnswer(answer -> {
      GatewaySocialMessage message = answer.getArgument(2);
      message.setTextContent("message decrypted");
      return answer;
    }).when(messageDecryptor).decrypt(any(), eq(whatsAppUser.getSymphonyUserId()), any());
    when(adminClient.canChat("1", "federatedUserId", "WHATSAPP")).thenReturn(Optional.of(CanChatResponse.CAN_CHAT));
    when(authenticationService.getUserInfo(podConfiguration.getUrl(), new StaticSessionSupplier<>(session), true)).thenReturn(Optional.of(receipter));

    forwarderQueueConsumer.consume(notification, "1");

    verify(symphonyMessageSender, never()).sendAlertMessage(any(), anyString(), anyString());
  }

  @Test
  public void onRoomMessage() throws Exception {
    FederatedAccount whatsAppUser = FederatedAccount.builder()
      .emailAddress("emailAddress@symphony.com")
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

    SymphonySession session = datafeedSessionPool.listenDatafeed(whatsAppUser);
    federatedAccountRepository.save(whatsAppUser);

    String notification = getSnsSocialMessage("196", getEnvelopeMessage(getRoomMessageMaestroMessage(
      whatsAppUser,
      sender,
      bot
    )));
    // this method is annoying to mock
    doAnswer(answer -> {
      GatewaySocialMessage message = answer.getArgument(2);
      message.setTextContent("message decrypted");
      return answer;
    }).when(messageDecryptor).decrypt(any(), eq(whatsAppUser.getSymphonyUserId()), any());
    when(authenticationService.getUserInfo(podConfiguration.getUrl(), new StaticSessionSupplier<>(session), true)).thenReturn(Optional.of(receipter));
    long messageNumber = empClient.getMessages().size();
    forwarderQueueConsumer.consume(notification, "1");
    assertEquals(messageNumber + 1, empClient.getMessages().size());
    verify(symphonyMessageSender, never()).sendAlertMessage(any(), anyString(), anyString());
  }

  @Test
  public void onRoomMessage_fromBot() throws Exception {
    FederatedAccount whatsAppUser = FederatedAccount.builder()
      .emailAddress("emailAddress@symphony.com")
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

    SymphonySession session = datafeedSessionPool.listenDatafeed(whatsAppUser);
    federatedAccountRepository.save(whatsAppUser);

    String notification = getSnsSocialMessage("196", getEnvelopeMessage(getRoomMessageMaestroMessage(
      whatsAppUser,
      bot,
      sender
    )));
    // this method is annoying to mock
    doAnswer(answer -> {
      GatewaySocialMessage message = answer.getArgument(2);
      message.setTextContent("message decrypted");
      return answer;
    }).when(messageDecryptor).decrypt(any(), eq(whatsAppUser.getSymphonyUserId()), any());
    when(authenticationService.getUserInfo(podConfiguration.getUrl(), new StaticSessionSupplier<>(session), true)).thenReturn(Optional.of(receipter));
    long messageNumber = empClient.getMessages().size();
    forwarderQueueConsumer.consume(notification, "1");
    assertEquals(messageNumber, empClient.getMessages().size());
    verify(symphonyMessageSender, never()).sendAlertMessage(any(), anyString(), anyString());
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
      "      \"emailAddress\":\"" + whatsAppUserInvited.getEmailAddress() + "\"," +
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
  private String getRoomMessageMaestroMessage(FederatedAccount whatsAppUser, UserInfo... otherParticipants) {
    return getMessageMaestroMessage(whatsAppUser, Arrays.asList(otherParticipants), "CHATROOM");
  }
  private String getMIMMessageMaestroMessage(FederatedAccount whatsAppUser, UserInfo... otherParticipants) {
    return getMessageMaestroMessage(whatsAppUser, Arrays.asList(otherParticipants), "INSTANT_CHAT");
  }
  private String getMessageMaestroMessage(FederatedAccount whatsAppUser, List<UserInfo> participants, String chatType) {
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
      "  ]," +
      "  \"encryptedEntities\":\"AgAAAKcAAAAAAAAAAJ6O1SMRXgf68fdt6/pZaSHQvxIWpgq2hbHDhAiuAbnQow8SgdSrv4WAFYyUIHqT3EFBYK6AC4ZG/YfxEAVM9IJfKZ5pU5dXxoTjxU5VmpASlXY=\"," +
      "  \"encryptedMedia\":\"AgAAAKcAAAAAAAAAAP5fS7NnNyx1o+gq5kzQQ2XshB3EF3nTsDxvM2uD6yMebqWOnRdUSRCbz/dfMQ0UBVHpYBy7CxuXJm6g3bhJ2glBdl3VO9Kmn+gzn7EpJ1todA==\"," +
      "  \"enforceExpressionFiltering\":true," +
      "  \"entities\":{" +
      "  }," +
      "  \"externalOrigination\":false," +
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

  private String getEnvelopeMessage(String payload) {
    return "{" +
      "  \"_type\":\"com.symphony.s2.model.core.Envelope\"," +
      "  \"_version\":\"1.0\"," +
      "  \"createdDate\":\"2020-03-17T12:39:59.117Z\"," +
      "  \"distributionList\":[" +
      "    13469017440257" +
      "  ]," +
      "  \"notificationDate\":\"2020-03-17T12:39:59.407Z\"," +
      "  \"payload\":" + payload + "," +
      "  \"payloadType\":\"com.symphony.s2.model.chat.MaestroMessage.CONNECTION_REQUEST_ALERT\"," +
      "  \"podId\":196," +
      "  \"purgeDate\":\"2027-03-16T12:39:59.117Z\"" +
      "}";
  }
}
