package com.symphony.sfs.ms.chat.service;

import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.oss.models.core.canon.facade.PodAndUserId;
import com.symphony.sfs.ms.admin.generated.model.CanChatResponse;
import com.symphony.sfs.ms.admin.generated.model.EmpList;
import com.symphony.sfs.ms.admin.generated.model.EntitlementResponse;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.datafeed.GatewaySocialMessage;
import com.symphony.sfs.ms.chat.datafeed.ParentRelationshipType;
import com.symphony.sfs.ms.chat.generated.model.SendMessageRequest.FormattingEnum;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyService;
import com.symphony.sfs.ms.emp.generated.model.OperationIdBySymId;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageRequest;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageRequest.TypeEnum;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.config.properties.common.PemResource;
import com.symphony.sfs.ms.starter.health.MeterManager;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamAttributes;
import com.symphony.sfs.ms.starter.symphony.stream.StreamInfo;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.symphony.stream.StreamType;
import com.symphony.sfs.ms.starter.symphony.stream.StreamTypes;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageServiceTest {


  private SymphonyMessageService messageService;

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

  private SymphonySession userSession;

  private static final long NOW = OffsetDateTime.now().toEpochSecond();
  public static final String FROM_SYMPHONY_USER_ID = "123456789";
  public static final String TO_SYMPHONY_USER_ID = "234567891";

  @BeforeEach
  public void setUp() {
    MeterManager meterManager = new MeterManager(new SimpleMeterRegistry(), Optional.empty());
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

    symphonyMessageSender = mock(SymphonyMessageSender.class);
    symphonyService = mock(SymphonyService.class);

    streamService = mock(StreamService.class);

    userSession = new SymphonySession("username", "kmToken", "sessionToken");
    when(authenticationService.authenticate(anyString(), anyString(), anyString(), anyString())).thenReturn(userSession);

    adminClient = mock(AdminClient.class);
    when(adminClient.getEmpList()).thenReturn(new EmpList());

    empSchemaService = new EmpSchemaService(adminClient);

    channelService = mock(ChannelService.class);

    messageService = new SymphonyMessageService(empClient, federatedAccountRepository, mock(ForwarderQueueConsumer.class), datafeedSessionPool, symphonyMessageSender, adminClient, empSchemaService, symphonyService, podConfiguration, botConfiguration, authenticationService, null, streamService, new MessageIOMonitor(meterManager), channelService);
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
    orderVerifier.verify(empClient, never()).sendMessage("emp", "streamId", "messageId", fromSymphonyUser, Collections.singletonList(federatedAccount101), now, "message", "disclaimer", null);
    orderVerifier.verify(empClient, once()).sendMessage("emp", "streamId", "messageId", fromSymphonyUser, Collections.singletonList(federatedAccount101), now, "message", "", null);
    orderVerifier.verifyNoMoreInteractions();

    // With disclaimer
    GatewaySocialMessage messageWithDisclaimer = GatewaySocialMessage.builder().streamId("streamId").messageId("messageId").fromUser(fromSymphonyUser).members(members).timestamp(now).textContent("message").disclaimer("disclaimer").build();
    messageService.onIMMessage(messageWithDisclaimer);

    orderVerifier.verify(empClient, once()).sendMessage("emp", "streamId", "messageId", fromSymphonyUser, Collections.singletonList(federatedAccount101), now, "message", "disclaimer", null);
    orderVerifier.verify(empClient, never()).sendMessage("emp", "streamId", "messageId", fromSymphonyUser, Collections.singletonList(federatedAccount101), now, "message", "", null);
    orderVerifier.verifyNoMoreInteractions();
  }

  private static Stream<Arguments> textProvider() {
    return Stream.of(
      arguments("text", "text"),
      arguments("<b onclick=\"alert('hello')\">text</b>", "&lt;b onclick=\"alert('hello')\"&gt;text&lt;/b&gt;")
    );
  }

  @ParameterizedTest
  @MethodSource("textProvider")
  void onIMMessage(String inputText, String expectedSentText) {
    EntitlementResponse response = new EntitlementResponse();
    when(adminClient.canChat(FROM_SYMPHONY_USER_ID, "fed", "emp")).thenReturn(Optional.of(CanChatResponse.CAN_CHAT));
    FederatedAccount toFederatedAccount = buildDefaultToFederatedAccount();

    IUser fromSymphonyUser = buildDefaultFromUser();

    when(federatedAccountRepository.findBySymphonyId(TO_SYMPHONY_USER_ID)).thenReturn(Optional.of(toFederatedAccount));
    List<OperationIdBySymId> empResult = List.of(new OperationIdBySymId().symphonyId(toFederatedAccount.getSymphonyUserId()).operationId("leaseId"));
    when(empClient.sendMessage("emp", "streamId", "messageId", fromSymphonyUser, toFederatedAccount, NOW, expectedSentText, null, null)).thenReturn(Optional.of(empResult));
    GatewaySocialMessage message = GatewaySocialMessage.builder().streamId("streamId").messageId("messageId").fromUser(fromSymphonyUser).members(Arrays.asList(FROM_SYMPHONY_USER_ID, TO_SYMPHONY_USER_ID)).timestamp(NOW).textContent(inputText).build();
    messageService.onIMMessage(message);

    verify(federatedAccountRepository, once()).findBySymphonyId(FROM_SYMPHONY_USER_ID);
    verify(federatedAccountRepository, once()).findBySymphonyId(TO_SYMPHONY_USER_ID);
    verify(empClient, once()).sendMessage("emp", "streamId", "messageId", fromSymphonyUser, Collections.singletonList(toFederatedAccount), NOW, expectedSentText, "", null);
  }

  @Test
  void onIMMessage_No_entitlements() {
    EntitlementResponse response = new EntitlementResponse();
    when(adminClient.canChat(FROM_SYMPHONY_USER_ID, "fed", "emp")).thenReturn(Optional.of(CanChatResponse.NO_ENTITLEMENT));
    FederatedAccount toFederatedAccount = FederatedAccount.builder()
      .emp("emp")
      .symphonyUserId(TO_SYMPHONY_USER_ID)
      .federatedUserId("fed")
      .build();

    IUser fromSymphonyUser = newIUser(FROM_SYMPHONY_USER_ID);

    when(federatedAccountRepository.findBySymphonyId(TO_SYMPHONY_USER_ID)).thenReturn(Optional.of(toFederatedAccount));
    List<OperationIdBySymId> empResult = List.of(new OperationIdBySymId().symphonyId(toFederatedAccount.getSymphonyUserId()).operationId("leaseId"));
    when(empClient.sendMessage("emp", "streamId", "messageId", fromSymphonyUser, toFederatedAccount, NOW, "text", null, null)).thenReturn(Optional.of(empResult));
    GatewaySocialMessage message = GatewaySocialMessage.builder().streamId("streamId").messageId("messageId").fromUser(fromSymphonyUser).members(Arrays.asList(FROM_SYMPHONY_USER_ID, TO_SYMPHONY_USER_ID)).timestamp(NOW).textContent("text").build();
    messageService.onIMMessage(message);

    verify(federatedAccountRepository, once()).findBySymphonyId(FROM_SYMPHONY_USER_ID);
    verify(federatedAccountRepository, once()).findBySymphonyId(TO_SYMPHONY_USER_ID);
    verify(empClient, never()).sendMessage("emp", "streamId", "messageId", fromSymphonyUser, Collections.singletonList(toFederatedAccount), NOW, "text", "", null);
    // session is mocked, null for now
    verify(symphonyMessageSender, once()).sendAlertMessage(null, "streamId", "You are not entitled to send messages to emp users.");
  }


  @Test
  void onIMMessage_No_PartiallySent() {
    EntitlementResponse response = new EntitlementResponse();
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
    when(empClient.sendMessage("emp", "streamId", "messageId", fromSymphonyUser, List.of(toFedAcc1, toFedAcc2), NOW, "text", "", null)).thenReturn(Optional.of(empResult));
    GatewaySocialMessage message = GatewaySocialMessage.builder().streamId("streamId").messageId("messageId").fromUser(fromSymphonyUser).members(Arrays.asList(id1, id2, TO_SYMPHONY_USER_ID)).timestamp(NOW).textContent("text").chatType("CHATROOM").build();

    messageService.onIMMessage(message);
//
    // session is mocked, null for now
    verify(symphonyMessageSender, once()).sendAlertMessage(new SymphonySession("username", "kmToken", "sessionToken"), "streamId", "This message (messageId : messageId) was not delivered for the following users: firstName 2 lastName 2");
  }

  @Test
  void onIMMessage_No_Contact() {
    EntitlementResponse response = new EntitlementResponse();
    when(adminClient.canChat(FROM_SYMPHONY_USER_ID, "fed", "emp")).thenReturn(Optional.of(CanChatResponse.NO_CONTACT));
    FederatedAccount toFederatedAccount = FederatedAccount.builder()
      .emp("emp")
      .symphonyUserId(TO_SYMPHONY_USER_ID)
      .federatedUserId("fed")
      .build();

    IUser fromSymphonyUser = newIUser(FROM_SYMPHONY_USER_ID);

    when(federatedAccountRepository.findBySymphonyId(TO_SYMPHONY_USER_ID)).thenReturn(Optional.of(toFederatedAccount));
    List<OperationIdBySymId> empResult = List.of(new OperationIdBySymId().symphonyId(toFederatedAccount.getSymphonyUserId()).operationId("leaseId"));
    when(empClient.sendMessage("emp", "streamId", "messageId", fromSymphonyUser, toFederatedAccount, NOW, "text", null, null)).thenReturn(Optional.of(empResult));
    GatewaySocialMessage message = GatewaySocialMessage.builder().streamId("streamId").messageId("messageId").fromUser(fromSymphonyUser).members(Arrays.asList(FROM_SYMPHONY_USER_ID, TO_SYMPHONY_USER_ID)).timestamp(NOW).textContent("text").build();
    messageService.onIMMessage(message);

    verify(federatedAccountRepository, once()).findBySymphonyId(FROM_SYMPHONY_USER_ID);
    verify(federatedAccountRepository, once()).findBySymphonyId(TO_SYMPHONY_USER_ID);
    verify(empClient, never()).sendMessage("emp", "streamId", "messageId", fromSymphonyUser, Collections.singletonList(toFederatedAccount), NOW, "text", "", null);
    // session is mocked, null for now
    verify(symphonyMessageSender, once()).sendAlertMessage(null, "streamId", "This message will not be delivered. You no longer have the entitlement for this.");
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
        "Chimes are not supported currently, your contact was not notified."),
      arguments(toFederatedAccount,
        GatewaySocialMessage.builder().streamId("streamId").messageId("messageId").fromUser(fromSymphonyUser).members(Arrays.asList(FROM_SYMPHONY_USER_ID, TO_SYMPHONY_USER_ID)).timestamp(NOW).textContent("text").parentRelationshipType(ParentRelationshipType.REPLY).build(),
        "Your message was not sent to your contact. Inline replies are not supported currently."),
      arguments(toFederatedAccount,
        GatewaySocialMessage.builder().streamId("streamId").messageId("messageId").fromUser(fromSymphonyUser).members(Arrays.asList(FROM_SYMPHONY_USER_ID, TO_SYMPHONY_USER_ID)).timestamp(NOW).textContent("text").table(true).build(),
        "Your message was not sent. Sending tables is not supported currently.")
      );
  }

  @ParameterizedTest
  @MethodSource("unsupportedGatewaySocialMessageProvider")
  void onIMMessage_UnsupportedContents(FederatedAccount toFederatedAccount, GatewaySocialMessage message, String expectedAlertMessage) {
    when(federatedAccountRepository.findBySymphonyId(TO_SYMPHONY_USER_ID)).thenReturn(Optional.of(toFederatedAccount));
    //when(empClient.sendMessage("emp", "streamId", "messageId", fromSymphonyUser, toFederatedAccount, NOW, "text", null, null)).thenReturn(Optional.of("leaseId"));
    messageService.onIMMessage(message);

    verify(empClient, never()).sendMessage("emp", "streamId", "messageId", message.getFromUser(), Collections.singletonList(toFederatedAccount), NOW, "text", "", null);
    // session is mocked, null for now
    verify(symphonyMessageSender, once()).sendAlertMessage(null, "streamId", expectedAlertMessage);
  }

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

    messageService.sendMessage("streamId", FROM_SYMPHONY_USER_ID, null, "text", null);
    verify(symphonyMessageSender, once()).sendRawMessage("streamId", FROM_SYMPHONY_USER_ID, "<messageML>text</messageML>", null);
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

    messageService.sendMessage("streamId", FROM_SYMPHONY_USER_ID, null, tooLongMsg, null);
    String expectedTruncatedMsg = "<messageML>" + tooLongMsg.substring(0, 30000) + "</messageML>";
    verify(symphonyMessageSender, once()).sendRawMessage("streamId", FROM_SYMPHONY_USER_ID, expectedTruncatedMsg, null);
    verify(symphonyMessageSender, once()).sendAlertMessage("streamId", FROM_SYMPHONY_USER_ID, "The message was too long and was truncated. Only the first 30,000 characters were delivered", null);
    verify(empClient, once()).sendSystemMessage(eq("emp"), eq("streamId"), any(), any(), eq("The message was too long and was truncated. Only the first 30,000 characters were delivered"), eq(TypeEnum.ALERT));
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
