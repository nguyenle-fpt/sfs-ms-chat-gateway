package com.symphony.sfs.ms.chat.api;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.symphony.sfs.ms.chat.config.DynamoConfiguration;
import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.generated.model.CreateAccountRequest;
import com.symphony.sfs.ms.chat.generated.model.CreateAccountResponse;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.symphony.AdminUserManagementService;
import com.symphony.sfs.ms.chat.service.FederatedAccountService;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyUser;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyUserAttributes;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyUserSystemAttributes;
import com.symphony.sfs.ms.starter.config.ExceptionHandling;
import com.symphony.sfs.ms.starter.config.JacksonConfiguration;
import com.symphony.sfs.ms.starter.config.properties.AwsDynamoConfiguration;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.config.properties.common.Key;
import com.symphony.sfs.ms.starter.dynamo.schema.DynamoSchema;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.UserSession;
import com.symphony.sfs.ms.starter.testing.DynamoTest;
import com.symphony.sfs.ms.starter.testing.LocalProfileTest;
import com.symphony.sfs.ms.starter.testing.RestApiTest;
import io.fabric8.mockwebserver.DefaultMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import javax.net.ssl.SSLException;

import static clients.symphony.api.constants.PodConstants.ADMINCREATEUSER;
import static com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool.DatafeedSession;
import static com.symphony.sfs.ms.chat.generated.api.AccountsApi.CREATEACCOUNT_ENDPOINT;
import static com.symphony.sfs.ms.starter.testing.DynamoTestUtils.createTable;
import static com.symphony.sfs.ms.starter.testing.DynamoTestUtils.deleteTable;
import static com.symphony.sfs.ms.starter.testing.DynamoTestUtils.tableExists;
import static com.symphony.sfs.ms.starter.testing.MockMvcUtils.configuredGiven;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AccountsApiTest implements DynamoTest, LocalProfileTest, RestApiTest {

  private AccountsApi accountsApi;
  private AuthenticationService authenticationService;
  private FederatedAccountRepository federatedAccountRepository;
  private FederatedAccountService federatedAccountService;
  private ObjectMapper objectMapper;
  private DynamoConfiguration dynamoConfiguration;
  private PodConfiguration podConfiguration;
  private ChatConfiguration chatConfiguration;
  private BotConfiguration botConfiguration;
  private DatafeedSessionPool datafeedSessionPool;

  @BeforeEach
  public void setUp(AmazonDynamoDB db) throws SSLException {
    objectMapper = new JacksonConfiguration().configureJackson(new ObjectMapper());

    authenticationService = mock(AuthenticationService.class);

    AwsDynamoConfiguration awsDynamoConfiguration = new AwsDynamoConfiguration();
    awsDynamoConfiguration.getDynamodb().setTableName("TestTable");
    dynamoConfiguration = new DynamoConfiguration(awsDynamoConfiguration);

    podConfiguration = new PodConfiguration();
    botConfiguration = new BotConfiguration();
    botConfiguration.setPrivateKey(mock(Key.class));
    botConfiguration.setUsername("bot");

    chatConfiguration = new ChatConfiguration();
    chatConfiguration.setSharedPrivateKey(mock(Key.class));
    chatConfiguration.setSharedPublicKey(mock(Key.class));

    datafeedSessionPool = new DatafeedSessionPool(authenticationService, podConfiguration, chatConfiguration);

    federatedAccountRepository = new FederatedAccountRepository(db, dynamoConfiguration.getDynamoSchema());
    federatedAccountService = new FederatedAccountService(
      datafeedSessionPool,
      federatedAccountRepository,
      new AdminUserManagementService(buildTestClient()),
      authenticationService,
      podConfiguration,
      botConfiguration,
      chatConfiguration);
    accountsApi = new AccountsApi(federatedAccountService);

    createTable(db, dynamoConfiguration.getDynamoSchema());
  }

  @AfterEach
  public void tearDown(AmazonDynamoDB db) {
    DynamoSchema schema = dynamoConfiguration.getDynamoSchema();
    if (tableExists(db, schema.getTableName())) {
      deleteTable(db, schema.getTableName());
    }
  }

  @Test
  public void createAccountWithoutAdvisors(DefaultMockServer mockServer) {
    podConfiguration.setUrl("https://localhost:" + mockServer.getPort());

    UserSession botSession = new UserSession(botConfiguration.getUsername(), "jwt", "kmToken", "sessionToken");
    DatafeedSession accountSession = new DatafeedSession(new UserSession("username", "jwt", "kmToken", "sessionToken"), 1L);

    when(authenticationService.authenticate(any(), any(), eq(botConfiguration.getUsername()), (String) isNull())).thenReturn(botSession);
    when(authenticationService.authenticate(any(), any(), eq(accountSession.getUsername()), (String) isNull())).thenReturn(accountSession);

    SymphonyUser symphonyUser = new SymphonyUser();
    symphonyUser.setUserSystemInfo(SymphonyUserSystemAttributes.builder().id(accountSession.getUserId()).build());
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
      .federatedUserId("federatedUserId")
      .emp("WHATSAPP");

    CreateAccountResponse response = configuredGiven(objectMapper, new ExceptionHandling(), accountsApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(createAccountRequest)
      .when()
      .post(CREATEACCOUNT_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value())
      .extract().response().body()
      .as(CreateAccountResponse.class);

    assertEquals(new CreateAccountResponse()
      .symphonyUserId(accountSession.getUserId().toString())
      .symphonyUsername(accountSession.getUsername()), response);

    DatafeedSession session = datafeedSessionPool.getSession(accountSession.getUsername());
    assertEquals(accountSession, session);

    FederatedAccount expectedAccount = FederatedAccount.builder()
      .emailAddress(createAccountRequest.getEmailAddress())
      .phoneNumber(createAccountRequest.getPhoneNumber())
      .firstName(createAccountRequest.getFirstName())
      .lastName(createAccountRequest.getLastName())
      .federatedUserId(createAccountRequest.getFederatedUserId())
      .emp(createAccountRequest.getEmp())
      .symphonyUserId(accountSession.getUserId().toString())
      .symphonyUsername(accountSession.getUsername())
      .build();
    FederatedAccount actualAccount = federatedAccountRepository.findByFederatedUserIdAndEmp(createAccountRequest.getFederatedUserId(), createAccountRequest.getEmp()).get();
    assertEquals(expectedAccount, actualAccount);
  }

  @Test
  public void createAccountWithoutAdvisors_AlreadyExists(DefaultMockServer mockServer) {
    podConfiguration.setUrl("https://localhost:" + mockServer.getPort());

    UserSession botSession = new UserSession(botConfiguration.getUsername(), "jwt", "kmToken", "sessionToken");
    when(authenticationService.authenticate(any(), any(), eq(botConfiguration.getUsername()), (String) isNull())).thenReturn(botSession);

    FederatedAccount existingAccount = FederatedAccount.builder()
      .emailAddress("emailAddress@symphony.com")
      .phoneNumber("+33601020304")
      .firstName("firstName")
      .lastName("lastName")
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
      .federatedUserId(existingAccount.getFederatedUserId())
      .emp(existingAccount.getEmp());

    configuredGiven(objectMapper, new ExceptionHandling(), accountsApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(createAccountRequest)
      .when()
      .post(CREATEACCOUNT_ENDPOINT)
      .then()
      .statusCode(HttpStatus.CONFLICT.value());
  }
}
