package com.symphony.sfs.ms.chat.api;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.symphony.sfs.ms.admin.generated.model.CanChatResponse;
import com.symphony.sfs.ms.admin.generated.model.EmpEntity;
import com.symphony.sfs.ms.chat.api.util.AbstractIntegrationTest;
import com.symphony.sfs.ms.chat.generated.model.CreateAccountRequest;
import com.symphony.sfs.ms.chat.generated.model.CreateAccountResponse;
import com.symphony.sfs.ms.chat.generated.model.CreateChannelRequest;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.ChannelService;
import com.symphony.sfs.ms.chat.service.EmpSchemaService;
import com.symphony.sfs.ms.chat.service.FederatedAccountService;
import com.symphony.sfs.ms.chat.service.external.MockAdminClient;
import com.symphony.sfs.ms.chat.service.external.MockEmpClient;
import com.symphony.sfs.ms.starter.config.ExceptionHandling;
import com.symphony.sfs.ms.starter.security.SessionManager;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.StringId;
import com.symphony.sfs.ms.starter.symphony.user.AdminUserManagementService;
import com.symphony.sfs.ms.starter.symphony.user.SymphonyUser;
import com.symphony.sfs.ms.starter.symphony.user.SymphonyUserAttributes;
import com.symphony.sfs.ms.starter.symphony.user.SymphonyUserSystemAttributes;
import com.symphony.sfs.ms.starter.symphony.user.UsersInfoService;
import com.symphony.sfs.ms.starter.symphony.xpod.ConnectionRequestStatus;
import io.fabric8.mockwebserver.DefaultMockServer;
import io.opentracing.Tracer;
import model.InboundConnectionRequest;
import model.UserInfo;
import model.UserInfoList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import static clients.symphony.api.constants.PodConstants.ADMINCREATEUSER;
import static clients.symphony.api.constants.PodConstants.ADMINUPDATEUSER;
import static clients.symphony.api.constants.PodConstants.GETCONNECTIONSTATUS;
import static clients.symphony.api.constants.PodConstants.GETIM;
import static clients.symphony.api.constants.PodConstants.GETUSERSV3;
import static clients.symphony.api.constants.PodConstants.SENDCONNECTIONREQUEST;
import static clients.symphony.api.constants.PodConstants.UPDATEUSERSTATUSADMIN;
import static com.symphony.sfs.ms.chat.api.util.SnsMessageUtil.getAcceptedConnectionRequestMaestroMessage;
import static com.symphony.sfs.ms.chat.api.util.SnsMessageUtil.getEnvelopeMessage;
import static com.symphony.sfs.ms.chat.api.util.SnsMessageUtil.getSnsMaestroMessage;
import static com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool.DatafeedSession;
import static com.symphony.sfs.ms.chat.generated.api.AccountsApi.CREATEACCOUNT_ENDPOINT;
import static com.symphony.sfs.ms.chat.generated.api.AccountsApi.DELETEFEDERATEDACCOUNT_ENDPOINT;
import static com.symphony.sfs.ms.chat.generated.api.ChannelsApi.CREATECHANNEL_ENDPOINT;
import static com.symphony.sfs.ms.starter.testing.MockMvcUtils.configuredGiven;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AccountsApiTest extends AbstractIntegrationTest {

  protected FederatedAccountRepository federatedAccountRepository;
  protected FederatedAccountService federatedAccountService;
  protected AccountsApi accountsApi;
  protected EmpSchemaService empSchemaService;
  protected ChannelsApi channelApi;
  private Tracer tracer = mock(Tracer.class);

  @BeforeEach
  public void setUp(AmazonDynamoDB db, DefaultMockServer mockServer) throws Exception {
    super.setUp(db, mockServer);

    empSchemaService = mock(EmpSchemaService.class);

    SessionManager sessionManager = new SessionManager(webClient, Collections.emptyList());

    federatedAccountRepository = new FederatedAccountRepository(db, dynamoConfiguration.getDynamoSchema());
    federatedAccountService = new FederatedAccountService(
      datafeedSessionPool,
      federatedAccountRepository,
      new AdminUserManagementService(sessionManager),
      authenticationService,
      podConfiguration,
      botConfiguration,
      chatConfiguration,
      connectionRequestManager,
      channelService,
      forwarderQueueConsumer,
      new UsersInfoService(sessionManager),
      empSchemaService,
      empClient,
      symphonyAuthFactory,
      adminClient,
      channelRepository);
    federatedAccountService.registerAsDatafeedListener();
    channelApi = new ChannelsApi(federatedAccountService, channelService);

    accountsApi = new AccountsApi(federatedAccountService);
  }

  @Test
  public void createAccountWithoutAdvisors() {
    SymphonySession botSession = getSession(botConfiguration.getUsername());
    DatafeedSession accountSession = new DatafeedSession(getSession("username"), "1");

    EmpEntity empEntity = new EmpEntity()
      .name("WHATSAPP")
      .serviceAccountSuffix("WHATSAPP");
    when(empSchemaService.getEmpDefinition("WHATSAPP")).thenReturn(Optional.of(empEntity));
    when(authenticationService.authenticate(any(), any(), eq(botConfiguration.getUsername()), anyString())).thenReturn(botSession);
    when(authenticationService.authenticate(any(), any(), eq(accountSession.getUsername()), anyString())).thenReturn(accountSession);

    SymphonyUser symphonyUser = new SymphonyUser();
    symphonyUser.setUserSystemInfo(SymphonyUserSystemAttributes.builder().id(accountSession.getUserIdAsLong()).build());
    symphonyUser.setUserAttributes(SymphonyUserAttributes.builder().userName(accountSession.getUsername()).build());

    mockServer.expect()
      .post()
      .withPath(ADMINCREATEUSER)
      .andReturn(HttpStatus.OK.value(), symphonyUser)
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();

    CreateAccountRequest createAccountRequest = new CreateAccountRequest()
      .emailAddress("emailAddress@symphony.com")
      .phoneNumber("+33601020304")
      .firstName("firstName")
      .lastName("lastName")
      .companyName("companyName")
      .federatedUserId("federatedUserId")
      .emp("WHATSAPP");

    CreateAccountResponse response = configuredGiven(objectMapper, new ExceptionHandling(tracer), accountsApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(createAccountRequest)
      .when()
      .post(CREATEACCOUNT_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value())
      .extract().response().body()
      .as(CreateAccountResponse.class);

    assertEquals(new CreateAccountResponse()
      .symphonyUserId(accountSession.getUserId())
      .symphonyUsername(accountSession.getUsername()), response);

    DatafeedSession session = datafeedSessionPool.getSession(accountSession.getUserId());
    assertEquals(accountSession, session);

    FederatedAccount expectedAccount = FederatedAccount.builder()
      .emailAddress(createAccountRequest.getEmailAddress())
      .phoneNumber(createAccountRequest.getPhoneNumber())
      .firstName(createAccountRequest.getFirstName())
      .lastName(createAccountRequest.getLastName())
      .companyName(createAccountRequest.getCompanyName())
      .federatedUserId(createAccountRequest.getFederatedUserId())
      .emp(createAccountRequest.getEmp())
      .symphonyUserId(accountSession.getUserId())
      .symphonyUsername(accountSession.getUsername())
      .kmToken(accountSession.getKmToken())
      .sessionToken(accountSession.getSessionToken())
      .build();

    FederatedAccount actualAccount = federatedAccountRepository.findByFederatedUserIdAndEmp(createAccountRequest.getFederatedUserId(), createAccountRequest.getEmp()).get();
    assertEquals(expectedAccount, actualAccount);
  }

  @Test
  public void createAccountWithAdvisor_AlreadyConnected() {
    SymphonySession botSession = getSession(botConfiguration.getUsername());
    DatafeedSession accountSession = new DatafeedSession(getSession("username"), "1");

    EmpEntity empEntity = new EmpEntity()
      .name("WHATSAPP")
      .serviceAccountSuffix("WHATSAPP");
    when(empSchemaService.getEmpDefinition("WHATSAPP")).thenReturn(Optional.of(empEntity));
    when(authenticationService.authenticate(any(), any(), eq(botConfiguration.getUsername()), anyString())).thenReturn(botSession);
    when(authenticationService.authenticate(any(), any(), eq(accountSession.getUsername()), anyString())).thenReturn(accountSession);

    UserInfo userInfo = new UserInfo();
    userInfo.setId(2L);
    userInfo.setUsername("username");
    userInfo.setFirstName("firstName");
    userInfo.setLastName("lastName");
    userInfo.setCompany("companyName");

    UserInfoList userInfoList = new UserInfoList();
    userInfoList.setUsers(Collections.singletonList(userInfo));

    mockServer.expect()
      .get()
      .withPath(GETUSERSV3 + "?local=false&uid=" + userInfo.getId())
      .andReturn(HttpStatus.OK.value(), userInfoList)
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();

    SymphonyUser symphonyUser = new SymphonyUser();
    symphonyUser.setUserSystemInfo(SymphonyUserSystemAttributes.builder().id(accountSession.getUserIdAsLong()).build());
    symphonyUser.setUserAttributes(SymphonyUserAttributes.builder().userName(accountSession.getUsername()).build());

    mockServer.expect()
      .post()
      .withPath(ADMINCREATEUSER)
      .andReturn(HttpStatus.OK.value(), symphonyUser)
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();

    InboundConnectionRequest inboundConnectionRequest = new InboundConnectionRequest();
    inboundConnectionRequest.setStatus(ConnectionRequestStatus.ACCEPTED.toString());
    mockServer.expect()
      .get()
      .withPath(GETCONNECTIONSTATUS.replace("{userId}", "2"))
      .andReturn(HttpStatus.OK.value(), inboundConnectionRequest)
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();

    mockServer.expect()
      .post()
      .withPath(GETIM)
      .andReturn(HttpStatus.OK.value(), new StringId("streamId"))
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();

    CreateAccountRequest createAccountRequest = new CreateAccountRequest()
      .emailAddress("emailAddress@symphony.com")
      .phoneNumber("+33601020304")
      .firstName("firstName")
      .lastName("lastName")
      .companyName("companyName")
      .federatedUserId("federatedUserId")
      .emp("WHATSAPP");

    CreateAccountResponse response = configuredGiven(objectMapper, new ExceptionHandling(tracer), accountsApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(createAccountRequest)
      .when()
      .post(CREATEACCOUNT_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value())
      .extract().response().body()
      .as(CreateAccountResponse.class);

    assertEquals(new CreateAccountResponse()
      .symphonyUserId(accountSession.getUserId())
      .symphonyUsername(accountSession.getUsername()), response);
    CreateChannelRequest createChannelRequest = new CreateChannelRequest()
      .federatedUserId("federatedUserId")
      .advisorUserId("2")
      .emp("WHATSAPP");

    configuredGiven(objectMapper, new ExceptionHandling(tracer), channelApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(createChannelRequest)
      .when()
      .post(CREATECHANNEL_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value());

    assertEquals(1, ((MockEmpClient) empClient).getChannels().size());
  }

  @Test
  public void createAccountWithAdvisor_NeedConnectionRequest() throws IOException {
    SymphonySession botSession = getSession(botConfiguration.getUsername());
    DatafeedSession accountSession = new DatafeedSession(getSession("username"), "1");

    EmpEntity empEntity = new EmpEntity()
      .name("WHATSAPP")
      .serviceAccountSuffix("WHATSAPP");
    when(empSchemaService.getEmpDefinition("WHATSAPP")).thenReturn(Optional.of(empEntity));
    when(authenticationService.authenticate(any(), any(), eq(botConfiguration.getUsername()), anyString())).thenReturn(botSession);
    when(authenticationService.authenticate(any(), any(), eq(accountSession.getUsername()), anyString())).thenReturn(accountSession);

    SymphonyUser symphonyUser = new SymphonyUser();
    symphonyUser.setUserSystemInfo(SymphonyUserSystemAttributes.builder().id(accountSession.getUserIdAsLong()).build());
    symphonyUser.setUserAttributes(SymphonyUserAttributes.builder().userName(accountSession.getUsername()).build());

    mockServer.expect()
      .post()
      .withPath(ADMINCREATEUSER)
      .andReturn(HttpStatus.OK.value(), symphonyUser)
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();

    mockServer.expect()
      .get()
      .withPath(GETCONNECTIONSTATUS.replace("{userId}", "2"))
      .andReturn(HttpStatus.NOT_FOUND.value(), null)
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();

    InboundConnectionRequest inboundConnectionRequest = new InboundConnectionRequest();
    inboundConnectionRequest.setStatus(ConnectionRequestStatus.PENDING_OUTGOING.toString());
    mockServer.expect()
      .post()
      .withPath(SENDCONNECTIONREQUEST)
      .andReturn(HttpStatus.OK.value(), inboundConnectionRequest)
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();

    mockServer.expect()
      .post()
      .withPath(GETIM)
      .andReturn(HttpStatus.OK.value(), new StringId("streamId"))
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();

    CreateAccountRequest createAccountRequest = new CreateAccountRequest()
      .emailAddress("emailAddress@symphony.com")
      .phoneNumber("+33601020304")
      .firstName("firstName")
      .lastName("lastName")
      .companyName("companyName")
      .federatedUserId("federatedUserId")
      .emp("WHATSAPP");

    CreateAccountResponse response = configuredGiven(objectMapper, new ExceptionHandling(tracer), accountsApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(createAccountRequest)
      .when()
      .post(CREATEACCOUNT_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value())
      .extract().response().body()
      .as(CreateAccountResponse.class);

    assertEquals(new CreateAccountResponse()
      .symphonyUserId(accountSession.getUserId())
      .symphonyUsername(accountSession.getUsername()), response);

    assertEquals(0, ((MockEmpClient) empClient).getChannels().size());
    when(empClient.createChannel(any(), any(), any(), any(), any())).thenReturn(Optional.of("streamId"));
    String notification = getSnsMaestroMessage("196", getEnvelopeMessage(getAcceptedConnectionRequestMaestroMessage(
      FederatedAccount.builder()
        .emailAddress(createAccountRequest.getEmailAddress())
        .phoneNumber(createAccountRequest.getPhoneNumber())
        .firstName(createAccountRequest.getFirstName())
        .lastName(createAccountRequest.getLastName())
        .symphonyUserId(accountSession.getUserId())
        .symphonyUsername(accountSession.getUsername())
        .build(),
      FederatedAccount.builder()
        .symphonyUserId("2")
        .build()
    )));
    adminClient.setCanChatResponse(Optional.of(CanChatResponse.CAN_CHAT));
    forwarderQueueConsumer.consume(notification, "1");
    assertEquals(1, ((MockEmpClient) empClient).getChannels().size());
    assertEquals(1, adminClient.getImRequests().size());
    verify(empClient).createChannel(any(), any(), any(), any(), any());
  }
  @Test
  public void createAccountWithAdvisor_ThenDelete() throws IOException {
    SymphonySession botSession = getSession(botConfiguration.getUsername());
    DatafeedSession accountSession = new DatafeedSession(getSession("username"), "1");

    EmpEntity empEntity = new EmpEntity()
      .name("WHATSAPP")
      .serviceAccountSuffix("WHATSAPP");
    when(empSchemaService.getEmpDefinition("WHATSAPP")).thenReturn(Optional.of(empEntity));
    when(authenticationService.authenticate(any(), any(), eq(botConfiguration.getUsername()), anyString())).thenReturn(botSession);
    when(authenticationService.authenticate(any(), any(), eq(accountSession.getUsername()), anyString())).thenReturn(accountSession);

    SymphonyUser symphonyUser = new SymphonyUser();
    symphonyUser.setUserSystemInfo(SymphonyUserSystemAttributes.builder().id(accountSession.getUserIdAsLong()).build());
    symphonyUser.setUserAttributes(SymphonyUserAttributes.builder().userName(accountSession.getUsername()).build());

    mockServer.expect()
      .post()
      .withPath(ADMINCREATEUSER)
      .andReturn(HttpStatus.OK.value(), symphonyUser)
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();

    mockServer.expect()
      .get()
      .withPath(GETCONNECTIONSTATUS.replace("{userId}", "2"))
      .andReturn(HttpStatus.NOT_FOUND.value(), null)
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();

    InboundConnectionRequest inboundConnectionRequest = new InboundConnectionRequest();
    inboundConnectionRequest.setStatus(ConnectionRequestStatus.PENDING_OUTGOING.toString());
    mockServer.expect()
      .post()
      .withPath(SENDCONNECTIONREQUEST)
      .andReturn(HttpStatus.OK.value(), inboundConnectionRequest)
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();

    mockServer.expect()
      .post()
      .withPath(GETIM)
      .andReturn(HttpStatus.OK.value(), new StringId("streamId"))
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();

    CreateAccountRequest createAccountRequest = new CreateAccountRequest()
      .emailAddress("emailAddress@symphony.com")
      .phoneNumber("+33601020304")
      .firstName("firstName")
      .lastName("lastName")
      .companyName("companyName")
      .federatedUserId("federatedUserId")
      .emp("WHATSAPP");

    CreateAccountResponse response = configuredGiven(objectMapper, new ExceptionHandling(tracer), accountsApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(createAccountRequest)
      .when()
      .post(CREATEACCOUNT_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value())
      .extract().response().body()
      .as(CreateAccountResponse.class);

    assertEquals(new CreateAccountResponse()
      .symphonyUserId(accountSession.getUserId())
      .symphonyUsername(accountSession.getUsername()), response);

    assertEquals(0, ((MockEmpClient) empClient).getChannels().size());

    assertTrue(channelRepository.findByAdvisorSymphonyIdAndFederatedUserIdAndEmp("2", createAccountRequest.getFederatedUserId(), createAccountRequest.getEmp()).isEmpty());

    String notification = getSnsMaestroMessage("196", getEnvelopeMessage(getAcceptedConnectionRequestMaestroMessage(
      FederatedAccount.builder()
        .emailAddress(createAccountRequest.getEmailAddress())
        .phoneNumber(createAccountRequest.getPhoneNumber())
        .firstName(createAccountRequest.getFirstName())
        .lastName(createAccountRequest.getLastName())
        .symphonyUserId(accountSession.getUserId())
        .symphonyUsername(accountSession.getUsername())
        .build(),
      FederatedAccount.builder()
        .symphonyUserId("2")
        .build()
    )));
    adminClient.setCanChatResponse(Optional.of(CanChatResponse.CAN_CHAT));
    when(empClient.createChannel(any(), any(), any(), any(), any())).thenReturn(Optional.of("streamId"));
    forwarderQueueConsumer.consume(notification, "1");
    assertEquals(1, ((MockEmpClient) empClient).getChannels().size());

    verify(empClient).createChannel(any(), any(), any(), any(), any());
    assertEquals(1, adminClient.getImRequests().size());

    //assertTrue(channelRepository.findByAdvisorSymphonyIdAndFederatedUserIdAndEmp("2", createAccountRequest.getFederatedUserId(), createAccountRequest.getEmp()).isPresent());

    mockServer.expect()
      .post()
      .withPath(ADMINUPDATEUSER)
      .andReturn(HttpStatus.OK.value(), symphonyUser)
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();
    mockServer.expect()
      .post()
      .withPath(UPDATEUSERSTATUSADMIN)
      .andReturn(HttpStatus.OK.value(), null)
      .always();

    configuredGiven(objectMapper, new ExceptionHandling(tracer), accountsApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .when()
      .delete(DELETEFEDERATEDACCOUNT_ENDPOINT, createAccountRequest.getFederatedUserId(), createAccountRequest.getEmp())
      .then()
      .statusCode(HttpStatus.OK.value());


    assertTrue(channelRepository.findByAdvisorSymphonyIdAndFederatedUserIdAndEmp("2", createAccountRequest.getFederatedUserId(), createAccountRequest.getEmp()).isEmpty());
    assertTrue(federatedAccountRepository.findByFederatedUserId(createAccountRequest.getFederatedUserId()).isEmpty());
    assertTrue(((MockEmpClient) empClient).getDeletedFederatedAccounts().contains(accountSession.getUserId()));
  }

  @Test
  public void createAccountWithoutAdvisors_AlreadyExists() {
    SymphonySession botSession = getSession(botConfiguration.getUsername());
    when(authenticationService.authenticate(any(), any(), eq(botConfiguration.getUsername()), anyString())).thenReturn(botSession);

    FederatedAccount existingAccount = FederatedAccount.builder()
      .emailAddress("emailAddress@symphony.com")
      .phoneNumber("+33601020304")
      .firstName("firstName")
      .lastName("lastName")
      .companyName("companyName")
      .federatedUserId("federatedUserId")
      .emp("WHATSAPP")
      .symphonyUserId("userId")
      .symphonyUsername("username")
      .build();
    federatedAccountRepository.save(existingAccount);

    CreateAccountRequest createAccountRequest = new CreateAccountRequest()
      .emailAddress(existingAccount.getEmailAddress())
      .phoneNumber(existingAccount.getPhoneNumber())
      .firstName(existingAccount.getFirstName())
      .lastName(existingAccount.getLastName())
      .companyName(existingAccount.getCompanyName())
      .federatedUserId(existingAccount.getFederatedUserId())
      .emp(existingAccount.getEmp());
    // For some reason having a tracer here actually throws the exception which fails the test
    // even though we are correctly checking for the status code
    // TODO investigate why this happens
    configuredGiven(objectMapper, new ExceptionHandling(null), accountsApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(createAccountRequest)
      .when()
      .post(CREATEACCOUNT_ENDPOINT)
      .then()
      .statusCode(HttpStatus.CONFLICT.value());
  }
}
