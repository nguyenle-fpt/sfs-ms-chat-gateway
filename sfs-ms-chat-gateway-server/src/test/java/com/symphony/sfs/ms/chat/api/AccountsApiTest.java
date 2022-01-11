package com.symphony.sfs.ms.chat.api;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.symphony.sfs.ms.admin.generated.model.EmpEntity;
import com.symphony.sfs.ms.chat.api.util.AbstractIntegrationTest;
import com.symphony.sfs.ms.chat.generated.model.CreateAccountRequest;
import com.symphony.sfs.ms.chat.generated.model.CreateAccountResponse;
import com.symphony.sfs.ms.chat.generated.model.FederatedAccountNotFoundProblem;
import com.symphony.sfs.ms.chat.generated.model.UpdateAccountRequest;
import com.symphony.sfs.ms.chat.generated.model.UpdateAccountResponse;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.EmpSchemaService;
import com.symphony.sfs.ms.chat.service.FederatedAccountService;
import com.symphony.sfs.ms.chat.service.external.MockEmpClient;
import com.symphony.sfs.ms.starter.config.ExceptionHandling;
import com.symphony.sfs.ms.starter.security.SessionManager;
import com.symphony.sfs.ms.starter.security.SessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.user.AdminUserManagementService;
import com.symphony.sfs.ms.starter.symphony.user.SymphonyUser;
import com.symphony.sfs.ms.starter.symphony.user.SymphonyUserAttributes;
import com.symphony.sfs.ms.starter.symphony.user.SymphonyUserSystemAttributes;
import com.symphony.sfs.ms.starter.symphony.user.UsersInfoService;
import com.symphony.sfs.ms.starter.testing.TestUtils;
import io.fabric8.mockwebserver.DefaultMockServer;
import io.opentracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.zalando.problem.DefaultProblem;
import org.zalando.problem.Problem;

import java.io.IOException;
import java.security.PrivateKey;
import java.util.Collections;
import java.util.Optional;

import static clients.symphony.api.constants.PodConstants.ADMINCREATEUSER;
import static clients.symphony.api.constants.PodConstants.ADMINUPDATEUSER;
import static clients.symphony.api.constants.PodConstants.UPDATEUSERSTATUSADMIN;
import static com.symphony.sfs.ms.chat.generated.api.AccountsApi.CREATEACCOUNT_ENDPOINT;
import static com.symphony.sfs.ms.chat.generated.api.AccountsApi.DELETEFEDERATEDACCOUNT_ENDPOINT;
import static com.symphony.sfs.ms.chat.generated.api.AccountsApi.UPDATEFEDERATEDACCOUNT_ENDPOINT;
import static com.symphony.sfs.ms.starter.testing.MockMvcUtils.configuredGiven;
import static com.symphony.sfs.ms.starter.testing.MockitoUtils.once;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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
  protected AdminUserManagementService adminUserManagementService;
  protected ChannelsApi channelApi;
  private Tracer tracer = mock(Tracer.class);

  private static String CLIENT_POD_ID = "1";

  private static final Long DEFAULT_USER_ID =  1L;
  private static final String DEFAULT_USER_ID_STRING =  "1";
  private static final String DEFAULT_USERNAME = "username";


  @BeforeEach
  public void setUp(AmazonDynamoDB db, DefaultMockServer mockServer, MessageSource messageSource) throws Exception {
    super.setUp(db, mockServer, messageSource);

    empSchemaService = mock(EmpSchemaService.class);

    SessionManager sessionManager = new SessionManager(webClient, Collections.emptyList());

    federatedAccountRepository = new FederatedAccountRepository(db, dynamoConfiguration.getDynamoSchema());

    adminUserManagementService = spy(new AdminUserManagementService(sessionManager));

    federatedAccountService = new FederatedAccountService(
      datafeedSessionPool,
      federatedAccountRepository,
      adminUserManagementService,
      podConfiguration,
      chatConfiguration,
      new UsersInfoService(sessionManager),
      empSchemaService,
      empClient,
      channelRepository);
    channelApi = new ChannelsApi(channelService);

    accountsApi = new AccountsApi(federatedAccountService);


    when(authenticationService.authenticate(anyString(), anyString(), anyString(), any(PrivateKey.class))).thenAnswer(
      args -> {
        SymphonySession symphonySession = new SymphonySession();
        symphonySession.setUsername(args.getArgument(2, String.class));
        return symphonySession;
      }
    );
  }

  @Test
  public void createAccount() {
    SymphonySession botSession = getSession(botConfiguration.getUsername());
    SymphonySession accountSession = getSession(DEFAULT_USERNAME);

    EmpEntity empEntity = new EmpEntity()
      .name("WHATSAPP")
      .serviceAccountSuffix("WHATSAPP");
    when(empSchemaService.getEmpDefinition("WHATSAPP")).thenReturn(Optional.of(empEntity));
    when(authenticationService.authenticate(any(), any(), eq(botConfiguration.getUsername()), anyString())).thenReturn(botSession);
    when(authenticationService.authenticate(any(), any(), eq(accountSession.getUsername()), anyString())).thenReturn(accountSession);

    SymphonyUser symphonyUser = new SymphonyUser();
    symphonyUser.setUserSystemInfo(SymphonyUserSystemAttributes.builder().id(DEFAULT_USER_ID).build());
    symphonyUser.setUserAttributes(SymphonyUserAttributes.builder().userName(DEFAULT_USERNAME).build());

    mockServer.expect()
      .post()
      .withPath(ADMINCREATEUSER)
      .andReturn(HttpStatus.OK.value(), symphonyUser)
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();

    CreateAccountRequest createAccountRequest = createDefaultAccountRequest();

    CreateAccountResponse response = configuredGiven(objectMapper, new ExceptionHandling(tracer), accountsApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(createAccountRequest)
      .when()
      .post(CREATEACCOUNT_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value())
      .extract().response().body()
      .as(CreateAccountResponse.class);

    verify(adminUserManagementService, once()).createUser(eq(podConfiguration.getUrl()), any(SessionSupplier.class), argThat(args -> {
      SymphonyUserAttributes userAttributes = args.getUserAttributes();
      return (userAttributes.getDisplayName().equals("firstName lastName [WHATSAPP]") &&
        userAttributes.getUserName().equals("WHATSAPP.33601020304") &&
        userAttributes.getEmailAddress().equals("WHATSAPP.33601020304@symphony.com"));
    }));

    assertEquals(new CreateAccountResponse()
      .symphonyUserId(DEFAULT_USER_ID_STRING)
      .symphonyUsername(DEFAULT_USERNAME), response);


    FederatedAccount expectedAccount = FederatedAccount.builder()
      .phoneNumber(createAccountRequest.getPhoneNumber())
      .firstName(createAccountRequest.getFirstName())
      .lastName(createAccountRequest.getLastName())
      .companyName(createAccountRequest.getCompanyName())
      .federatedUserId(createAccountRequest.getFederatedUserId())
      .emp(createAccountRequest.getEmp())
      .symphonyUserId(DEFAULT_USER_ID_STRING)
      .symphonyUsername(DEFAULT_USERNAME)
      .build();

    FederatedAccount actualAccount = federatedAccountRepository.findByFederatedUserIdAndEmp(createAccountRequest.getFederatedUserId(), createAccountRequest.getEmp()).get();
    assertEquals(expectedAccount, actualAccount);
  }

  @Test
  public void createAccount_DES_environment() {

    podConfiguration.setUsernameSuffix(".des");

    SymphonySession botSession = getSession(botConfiguration.getUsername());

    EmpEntity empEntity = new EmpEntity()
      .name("WHATSAPP")
      .serviceAccountSuffix("WHATSAPP");
    when(empSchemaService.getEmpDefinition("WHATSAPP")).thenReturn(Optional.of(empEntity));
    when(authenticationService.authenticate(any(), any(), eq(botConfiguration.getUsername()), anyString())).thenReturn(botSession);

    SymphonyUser symphonyUser = new SymphonyUser();
    symphonyUser.setUserSystemInfo(SymphonyUserSystemAttributes.builder().id(1L).build());
    symphonyUser.setUserAttributes(SymphonyUserAttributes.builder().userName("username").build());

    mockServer.expect()
      .post()
      .withPath(ADMINCREATEUSER)
      .andReturn(HttpStatus.OK.value(), symphonyUser)
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();

    CreateAccountRequest createAccountRequest = createDefaultAccountRequest();

    CreateAccountResponse response = configuredGiven(objectMapper, new ExceptionHandling(tracer), accountsApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(createAccountRequest)
      .when()
      .post(CREATEACCOUNT_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value())
      .extract().response().body()
      .as(CreateAccountResponse.class);

    verify(adminUserManagementService, once()).createUser(eq(podConfiguration.getUrl()), any(SessionSupplier.class), argThat(args -> {
      SymphonyUserAttributes userAttributes = args.getUserAttributes();
      return (userAttributes.getDisplayName().equals("firstName lastName [WHATSAPP]") &&
        userAttributes.getUserName().equals("WHATSAPP.33601020304.des") &&
        userAttributes.getEmailAddress().equals("WHATSAPP.33601020304.des@symphony.com"));
    }));

    assertEquals(new CreateAccountResponse()
      .symphonyUserId("1")
      .symphonyUsername("username"), response);

    FederatedAccount expectedAccount = FederatedAccount.builder()
      .phoneNumber(createAccountRequest.getPhoneNumber())
      .firstName(createAccountRequest.getFirstName())
      .lastName(createAccountRequest.getLastName())
      .companyName(createAccountRequest.getCompanyName())
      .federatedUserId(createAccountRequest.getFederatedUserId())
      .emp(createAccountRequest.getEmp())
      .symphonyUserId("1")
      .symphonyUsername("username")
      .build();

    FederatedAccount actualAccount = federatedAccountRepository.findByFederatedUserIdAndEmp(createAccountRequest.getFederatedUserId(), createAccountRequest.getEmp()).get();
    assertEquals(expectedAccount, actualAccount);
  }

  @Test
  public void createAccount_ThenDelete() {
    SymphonySession botSession = getSession(botConfiguration.getUsername());
    SymphonySession accountSession = getSession(DEFAULT_USERNAME);

    EmpEntity empEntity = new EmpEntity()
      .name("WHATSAPP")
      .serviceAccountSuffix("WHATSAPP");
    when(empSchemaService.getEmpDefinition("WHATSAPP")).thenReturn(Optional.of(empEntity));
    when(authenticationService.authenticate(any(), any(), eq(botConfiguration.getUsername()), anyString())).thenReturn(botSession);
    when(authenticationService.authenticate(any(), any(), eq(accountSession.getUsername()), anyString())).thenReturn(accountSession);

    SymphonyUser symphonyUser = new SymphonyUser();
    symphonyUser.setUserSystemInfo(SymphonyUserSystemAttributes.builder().id(DEFAULT_USER_ID).build());
    symphonyUser.setUserAttributes(SymphonyUserAttributes.builder().userName(accountSession.getUsername()).build());

    mockServer.expect()
      .post()
      .withPath(ADMINCREATEUSER)
      .andReturn(HttpStatus.OK.value(), symphonyUser)
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();

    CreateAccountRequest createAccountRequest = createDefaultAccountRequest();

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
      .symphonyUserId(DEFAULT_USER_ID_STRING)
      .symphonyUsername(accountSession.getUsername()), response);

    assertEquals(0, ((MockEmpClient) empClient).getChannels().size());

    assertTrue(channelRepository.findByAdvisorSymphonyIdAndFederatedUserIdAndEmp("2", createAccountRequest.getFederatedUserId(), createAccountRequest.getEmp()).isEmpty());

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
      .delete(DELETEFEDERATEDACCOUNT_ENDPOINT, createAccountRequest.getFederatedUserId(), createAccountRequest.getEmp(), CLIENT_POD_ID, true)
      .then()
      .statusCode(HttpStatus.OK.value());


    assertTrue(channelRepository.findByAdvisorSymphonyIdAndFederatedUserIdAndEmp("2", createAccountRequest.getFederatedUserId(), createAccountRequest.getEmp()).isEmpty());
    assertTrue(federatedAccountRepository.findByFederatedUserId(createAccountRequest.getFederatedUserId()).isEmpty());
    assertTrue(((MockEmpClient) empClient).getDeletedFederatedAccounts().contains(DEFAULT_USER_ID_STRING));
  }

  @Test
  public void createAccountWithAdvisor_ThenUpdate() {
    SymphonySession botSession = getSession(botConfiguration.getUsername());
    SymphonySession accountSession = getSession(DEFAULT_USERNAME);

    EmpEntity empEntity = new EmpEntity()
      .name("WHATSAPP")
      .serviceAccountSuffix("WHATSAPP");
    when(empSchemaService.getEmpDefinition("WHATSAPP")).thenReturn(Optional.of(empEntity));
    when(authenticationService.authenticate(any(), any(), eq(botConfiguration.getUsername()), anyString())).thenReturn(botSession);
    when(authenticationService.authenticate(any(), any(), eq(accountSession.getUsername()), anyString())).thenReturn(accountSession);

    SymphonyUser symphonyUser = new SymphonyUser();
    symphonyUser.setUserSystemInfo(SymphonyUserSystemAttributes.builder().id(DEFAULT_USER_ID).build());
    symphonyUser.setUserAttributes(SymphonyUserAttributes.builder().userName(accountSession.getUsername()).build());

    mockServer.expect()
      .post()
      .withPath(ADMINCREATEUSER)
      .andReturn(HttpStatus.OK.value(), symphonyUser)
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();

    CreateAccountRequest createAccountRequest = createDefaultAccountRequest();
    configuredGiven(objectMapper, new ExceptionHandling(tracer), accountsApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(createAccountRequest)
      .when()
      .post(CREATEACCOUNT_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value());

    mockServer.expect()
      .post()
      .withPath(ADMINUPDATEUSER)
      .andReturn(HttpStatus.OK.value(), symphonyUser)
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();

    UpdateAccountRequest updateAccountRequest = new UpdateAccountRequest()
      .phoneNumber(createAccountRequest.getPhoneNumber())
      .firstName("firstName2")
      .lastName("lastName2")
      .companyName("companyName2");

    UpdateAccountResponse updateAccountResponse = configuredGiven(objectMapper, new ExceptionHandling(tracer), accountsApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(updateAccountRequest)
      .when()
      .put(UPDATEFEDERATEDACCOUNT_ENDPOINT, createAccountRequest.getFederatedUserId(), CLIENT_POD_ID)
      .then()
      .statusCode(HttpStatus.OK.value())
      .extract().response().body()
      .as(UpdateAccountResponse.class);

    UpdateAccountResponse expectedUpdateAccountResponse = new UpdateAccountResponse()
      .firstName(updateAccountRequest.getFirstName())
      .lastName(updateAccountRequest.getLastName())
      .companyName(updateAccountRequest.getCompanyName())
      .phoneNumber(createAccountRequest.getPhoneNumber());
    assertEquals(expectedUpdateAccountResponse, updateAccountResponse);

    FederatedAccount expectedAccount = FederatedAccount.builder()
      .phoneNumber(createAccountRequest.getPhoneNumber())
      .firstName(updateAccountRequest.getFirstName())
      .lastName(updateAccountRequest.getLastName())
      .companyName(updateAccountRequest.getCompanyName())
      .federatedUserId(createAccountRequest.getFederatedUserId())
      .emp(createAccountRequest.getEmp())
      .symphonyUserId(DEFAULT_USER_ID_STRING)
      .symphonyUsername(accountSession.getUsername())
      .build();

    FederatedAccount actualAccount = federatedAccountRepository.findByFederatedUserIdAndEmp(createAccountRequest.getFederatedUserId(), createAccountRequest.getEmp()).get();
    assertEquals(expectedAccount, actualAccount);
  }

  @Test
  public void createMuliEmpAccount_ThenUpdate() throws IOException {
    SymphonySession botSession = getSession(botConfiguration.getUsername());
    SymphonySession accountSession = getSession(DEFAULT_USERNAME);
    SymphonySession accountSession2 = getSession("username2");

    EmpEntity empEntity = new EmpEntity()
      .name("WHATSAPP")
      .serviceAccountSuffix("WHATSAPP");
    EmpEntity empEntity2 = new EmpEntity()
      .name("SMS")
      .serviceAccountSuffix("SMS");
    when(empSchemaService.getEmpDefinition("WHATSAPP")).thenReturn(Optional.of(empEntity));
    when(empSchemaService.getEmpDefinition("SMS")).thenReturn(Optional.of(empEntity2));
    when(authenticationService.authenticate(any(), any(), eq(botConfiguration.getUsername()), anyString())).thenReturn(botSession);
    when(authenticationService.authenticate(any(), any(), eq(accountSession.getUsername()), anyString())).thenReturn(accountSession);
    when(authenticationService.authenticate(any(), any(), eq("username2"), anyString())).thenReturn(accountSession2);

    SymphonyUser symphonyUser = new SymphonyUser();
    symphonyUser.setUserSystemInfo(SymphonyUserSystemAttributes.builder().id(DEFAULT_USER_ID).build());
    symphonyUser.setUserAttributes(SymphonyUserAttributes.builder().userName(accountSession.getUsername()).build());
    SymphonyUser symphonyUser2 = new SymphonyUser();
    symphonyUser2.setUserSystemInfo(SymphonyUserSystemAttributes.builder().id(2L).build());
    symphonyUser2.setUserAttributes(SymphonyUserAttributes.builder().userName(accountSession2.getUsername()).build());

    mockServer.expect()
      .post()
      .withPath(ADMINCREATEUSER)
      .andReturn(HttpStatus.OK.value(), symphonyUser)
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();

    mockServer.expect()
      .post()
      .withPath(ADMINCREATEUSER)
      .andReturn(HttpStatus.OK.value(), symphonyUser2)
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();

    CreateAccountRequest createAccountRequest = createDefaultAccountRequest();
    CreateAccountRequest createAccountRequest2 = createDefaultAccountRequest().emp(empEntity2.getName());
    configuredGiven(objectMapper, new ExceptionHandling(tracer), accountsApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(createAccountRequest)
      .when()
      .post(CREATEACCOUNT_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value());
    configuredGiven(objectMapper, new ExceptionHandling(tracer), accountsApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(createAccountRequest2)
      .when()
      .post(CREATEACCOUNT_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value());
    mockServer.expect()
      .post()
      .withPath(ADMINUPDATEUSER)
      .andReturn(HttpStatus.OK.value(), symphonyUser)
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();
    mockServer.expect()
      .post()
      .withPath(ADMINUPDATEUSER)
      .andReturn(HttpStatus.OK.value(), symphonyUser2)
      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .always();

    UpdateAccountRequest updateAccountRequest = new UpdateAccountRequest()
      .phoneNumber(createAccountRequest.getPhoneNumber())
      .firstName("firstName2")
      .lastName("lastName2")
      .companyName("companyName2");

    UpdateAccountResponse updateAccountResponse = configuredGiven(objectMapper, new ExceptionHandling(tracer), accountsApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(updateAccountRequest)
      .when()
      .put(UPDATEFEDERATEDACCOUNT_ENDPOINT, createAccountRequest.getFederatedUserId(), CLIENT_POD_ID)
      .then()
      .statusCode(HttpStatus.OK.value())
      .extract().response().body()
      .as(UpdateAccountResponse.class);


    UpdateAccountResponse expectedUpdateAccountResponse = new UpdateAccountResponse()
      .firstName(updateAccountRequest.getFirstName())
      .lastName(updateAccountRequest.getLastName())
      .companyName(updateAccountRequest.getCompanyName())
      .phoneNumber(createAccountRequest.getPhoneNumber());
    assertEquals(expectedUpdateAccountResponse, updateAccountResponse);

    verify(empClient).updateAccountOrFail(empEntity.getName(), DEFAULT_USER_ID_STRING,  createAccountRequest.getPhoneNumber(), CLIENT_POD_ID, updateAccountRequest.getFirstName(), updateAccountRequest.getLastName(), updateAccountRequest.getCompanyName());
    verify(empClient).updateAccountOrFail(empEntity2.getName(), DEFAULT_USER_ID_STRING,  createAccountRequest.getPhoneNumber(), CLIENT_POD_ID, updateAccountRequest.getFirstName(), updateAccountRequest.getLastName(), updateAccountRequest.getCompanyName());

    FederatedAccount expectedAccount = FederatedAccount.builder()
      .phoneNumber(createAccountRequest.getPhoneNumber())
      .firstName(updateAccountRequest.getFirstName())
      .lastName(updateAccountRequest.getLastName())
      .companyName(updateAccountRequest.getCompanyName())
      .federatedUserId(createAccountRequest.getFederatedUserId())
      .emp(createAccountRequest.getEmp())
      .symphonyUserId(DEFAULT_USER_ID_STRING)
      .symphonyUsername(accountSession.getUsername())
      .build();

    FederatedAccount actualAccount = federatedAccountRepository.findByFederatedUserIdAndEmp(createAccountRequest.getFederatedUserId(), createAccountRequest.getEmp()).get();
    assertEquals(expectedAccount, actualAccount);

    actualAccount = federatedAccountRepository.findByFederatedUserIdAndEmp(createAccountRequest2.getFederatedUserId(), createAccountRequest2.getEmp()).get();
    expectedAccount.setEmp(createAccountRequest2.getEmp());
    assertEquals(expectedAccount, actualAccount);
  }

  @Test
  public void createAccountWithoutAdvisors_AlreadyExists() {
    SymphonySession botSession = getSession(botConfiguration.getUsername());
    when(authenticationService.authenticate(any(), any(), eq(botConfiguration.getUsername()), anyString())).thenReturn(botSession);

    FederatedAccount existingAccount = FederatedAccount.builder()
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

  @Test
  public void deleteAccount() {
    SymphonySession botSession = getSession(botConfiguration.getUsername());
    when(authenticationService.authenticate(any(), any(), eq(botConfiguration.getUsername()), anyString())).thenReturn(botSession);

    FederatedAccount existingAccount = FederatedAccount.builder()
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

    configuredGiven(objectMapper, new ExceptionHandling(null), accountsApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .when()
      .delete(DELETEFEDERATEDACCOUNT_ENDPOINT, "federatedUserId", "WHATSAPP", CLIENT_POD_ID, true)
      .then()
      .statusCode(HttpStatus.OK.value());
  }

  @Test
  public void deleteAccount_NotFound() {
    SymphonySession botSession = getSession(botConfiguration.getUsername());
    when(authenticationService.authenticate(any(), any(), eq(botConfiguration.getUsername()), anyString())).thenReturn(botSession);

    Problem actualProblem = configuredGiven(objectMapper, new ExceptionHandling(null), accountsApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .when()
      .delete(DELETEFEDERATEDACCOUNT_ENDPOINT, "federatedUserId", "WHATSAPP", CLIENT_POD_ID, true)
      .then()
      .statusCode(HttpStatus.NOT_FOUND.value())
      .extract().response().body()
      .as(DefaultProblem.class);

    FederatedAccountNotFoundProblem expectedProblem = new FederatedAccountNotFoundProblem();
    TestUtils.testProblemEquality(expectedProblem, actualProblem);
  }

  //====================
  // Helpers
  //====================

  CreateAccountRequest createDefaultAccountRequest() {
    return new CreateAccountRequest()
      .phoneNumber("+33601020304")
      .firstName("firstName")
      .lastName("lastName")
      .companyName("companyName")
      .federatedUserId("federatedUserId")
      .emp("WHATSAPP");
  }


}
