package com.symphony.sfs.ms.chat.api;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.symphony.sfs.ms.chat.api.util.AbstractIntegrationTest;
import com.symphony.sfs.ms.chat.generated.model.CreateAccountRequest;
import com.symphony.sfs.ms.chat.generated.model.CreateAccountResponse;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.FederatedAccountService;
import com.symphony.sfs.ms.chat.service.external.MockEmpClient;
import com.symphony.sfs.ms.chat.service.symphony.AdminUserManagementService;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyUser;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyUserAttributes;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyUserSystemAttributes;
import com.symphony.sfs.ms.starter.config.ExceptionHandling;
import com.symphony.sfs.ms.starter.symphony.auth.UserSession;
import com.symphony.sfs.ms.starter.symphony.stream.StringId;
import com.symphony.sfs.ms.starter.symphony.xpod.ConnectionRequestStatus;
import io.fabric8.mockwebserver.DefaultMockServer;
import model.InboundConnectionRequest;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.util.Collections;

import static clients.symphony.api.constants.PodConstants.ADMINCREATEUSER;
import static clients.symphony.api.constants.PodConstants.GETCONNECTIONSTATUS;
import static clients.symphony.api.constants.PodConstants.GETIM;
import static clients.symphony.api.constants.PodConstants.SENDCONNECTIONREQUEST;
import static com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool.DatafeedSession;
import static com.symphony.sfs.ms.chat.generated.api.AccountsApi.CREATEACCOUNT_ENDPOINT;
import static com.symphony.sfs.ms.starter.testing.MockMvcUtils.configuredGiven;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

public class AccountsApiTest extends AbstractIntegrationTest {

  protected FederatedAccountRepository federatedAccountRepository;
  protected FederatedAccountService federatedAccountService;
  protected AccountsApi accountsApi;

  @BeforeEach
  public void setUp(AmazonDynamoDB db, DefaultMockServer mockServer) throws SSLException {
    super.setUp(db, mockServer);

    federatedAccountRepository = new FederatedAccountRepository(db, dynamoConfiguration.getDynamoSchema());
    federatedAccountService = new FederatedAccountService(
      datafeedSessionPool,
      federatedAccountRepository,
      new AdminUserManagementService(webClient),
      authenticationService,
      podConfiguration,
      botConfiguration,
      chatConfiguration,
      connectionRequestManager,
      channelService);
    federatedAccountService.registerAsDatafeedListener(forwarderQueueConsumer);

    accountsApi = new AccountsApi(federatedAccountService);
  }

  @Test
  public void createAccountWithoutAdvisors() {
    UserSession botSession = getSession(botConfiguration.getUsername());
    DatafeedSession accountSession = new DatafeedSession(getSession("username"), "1");

    when(authenticationService.authenticate(any(), any(), eq(botConfiguration.getUsername()), (String) isNull())).thenReturn(botSession);
    when(authenticationService.authenticate(any(), any(), eq(accountSession.getUsername()), (String) isNull())).thenReturn(accountSession);

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
      .symphonyUserId(accountSession.getUserId())
      .symphonyUsername(accountSession.getUsername())
      .build();

    FederatedAccount actualAccount = federatedAccountRepository.findByFederatedUserIdAndEmp(createAccountRequest.getFederatedUserId(), createAccountRequest.getEmp()).get();
    assertEquals(expectedAccount, actualAccount);
  }

  @Test
  public void createAccountWithAdvisor_AlreadyConnected() {
    UserSession botSession = getSession(botConfiguration.getUsername());
    DatafeedSession accountSession = new DatafeedSession(getSession("username"), "1");

    when(authenticationService.authenticate(any(), any(), eq(botConfiguration.getUsername()), (String) isNull())).thenReturn(botSession);
    when(authenticationService.authenticate(any(), any(), eq(accountSession.getUsername()), (String) isNull())).thenReturn(accountSession);

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
      .federatedUserId("federatedUserId")
      .emp("WHATSAPP")
      .advisors(Collections.singletonList("2"));

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
      .symphonyUserId(accountSession.getUserId())
      .symphonyUsername(accountSession.getUsername()), response);

    assertEquals(1, ((MockEmpClient) empClient).getChannels().size());
  }

  @Test
  public void createAccountWithAdvisor_NeedConnectionRequest() throws IOException {
    UserSession botSession = getSession(botConfiguration.getUsername());
    DatafeedSession accountSession = new DatafeedSession(getSession("username"), "1");

    when(authenticationService.authenticate(any(), any(), eq(botConfiguration.getUsername()), (String) isNull())).thenReturn(botSession);
    when(authenticationService.authenticate(any(), any(), eq(accountSession.getUsername()), (String) isNull())).thenReturn(accountSession);

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
      .federatedUserId("federatedUserId")
      .emp("WHATSAPP")
      .advisors(Collections.singletonList("2"));

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
      .symphonyUserId(accountSession.getUserId())
      .symphonyUsername(accountSession.getUsername()), response);

    assertEquals(0, ((MockEmpClient) empClient).getChannels().size());

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
    forwarderQueueConsumer.consume(notification);

    assertEquals(1, ((MockEmpClient) empClient).getChannels().size());
  }

  @Test
  public void createAccountWithoutAdvisors_AlreadyExists() {
    UserSession botSession = getSession(botConfiguration.getUsername());
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

  private String getAcceptedConnectionRequestMaestroMessage(FederatedAccount requester, FederatedAccount requested) {
    return "{" +
      "  \"_type\":\"com.symphony.s2.model.chat.MaestroMessage\"," +
      "  \"affectedUsers\":[" +
      "    {" +
      "      \"companyName\":\"Symphony\"," +
      "      \"emailAddress\":\"" + requester.getEmailAddress() + "\"," +
      "      \"firstName\":\"" + requester.getFirstName() + "\"," +
      "      \"givenName\":\"" + requester.getFirstName() + "\"," +
      "      \"id\":" + requester.getSymphonyUserId() + "," +
      "      \"imageUrl\":\"../avatars/static/150/default.png\"," +
      "      \"imageUrlSmall\":\"../avatars/static/50/default.png\"," +
      "      \"images\":{" +
      "      }," +
      "      \"location\":\"\"," +
      "      \"prettyName\":\"" + requester.getFirstName() + " " + requester.getLastName() + "\"," +
      "      \"screenName\":\"" + requester.getFirstName() + "\"," +
      "      \"surname\":\"" + requester.getLastName() + "\"," +
      "      \"userType\":\"lc\"," +
      "      \"verifiedForBadge\":true" +
      "    }" +
      "  ]," +
      "  \"copyDisabled\":false," +
      "  \"event\":\"CONNECTION_REQUEST_ALERT\"," +
      "  \"fromPod\":196," +
      "  \"ingestionDate\":1584448898220," +
      "  \"isCopyDisabled\":false," +
      "  \"messageId\":\"jEt58ZHU580+FiQS40XhjH///o8XfHtTbw==\"," +
      "  \"payload\":{" +
      "    \"cargo\":{" +
      "      \"images\":{" +
      "        \"150\":\"../avatars/static/150/default.png\"," +
      "        \"50\":\"../avatars/static/50/default.png\"," +
      "        \"500\":\"../avatars/static/500/default.png\"," +
      "        \"600\":\"../avatars/static/600/default.png\"," +
      "        \"orig\":\"../avatars/static/orig/default.png\"" +
      "      }," +
      "      \"imgUrl\":\"../avatars/static/150/default.png\"," +
      "      \"imgUrlSmall\":\"../avatars/static/50/default.png\"," +
      "      \"initiatorUserId\":13469017440257," +
      "      \"requestCounter\":0," +
      "      \"status\":\"accepted\"," +
      "      \"targetUserId\":13606456395797" +
      "    }," +
      "    \"images\":{" +
      "      \"150\":\"../avatars/static/150/default.png\"," +
      "      \"50\":\"../avatars/static/50/default.png\"," +
      "      \"500\":\"../avatars/static/500/default.png\"," +
      "      \"600\":\"../avatars/static/600/default.png\"," +
      "      \"orig\":\"../avatars/static/orig/default.png\"" +
      "    }," +
      "    \"imgUrl\":\"../avatars/static/150/default.png\"," +
      "    \"imgUrlSmall\":\"../avatars/static/50/default.png\"," +
      "    \"requestCounter\":0," +
      "    \"status\":\"accepted\"," +
      "    \"targetUserId\":13606456395797," +
      "    \"version\":\"connectionRequestAlertPayload\"" +
      "  }," +
      "  \"podDistribution\":[" +
      "    196," +
      "    198" +
      "  ]," +
      "  \"requestingUser\":{" +
      "    \"company\":\"Symphony\"," +
      "    \"emailAddress\":\"" + requested.getEmailAddress() + "\"," +
      "    \"firstName\":\"" + requested.getFirstName() + "\"," +
      "    \"id\":" + requested.getSymphonyUserId() + "," +
      "    \"imageUrl\":\"../avatars/static/150/default.png\"," +
      "    \"imageUrlSmall\":\"../avatars/static/50/default.png\"," +
      "    \"images\":{" +
      "      \"150\":\"../avatars/static/150/default.png\"," +
      "      \"50\":\"../avatars/static/50/default.png\"," +
      "      \"500\":\"../avatars/static/500/default.png\"," +
      "      \"600\":\"../avatars/static/600/default.png\"," +
      "      \"orig\":\"../avatars/static/orig/default.png\"" +
      "    }," +
      "    \"prettyName\":\"" + requested.getFirstName() + " " + requested.getLastName() + "\"," +
      "    \"firstName\":\"" + requested.getLastName() + "\"," +
      "    \"userType\":\"lc\"," +
      "    \"username\":\"" + requested.getSymphonyUsername() + "\"" +
      "  }," +
      "  \"schemaVersion\":1," +
      "  \"semVersion\":\"1.56.0-SNAPSHOT\"," +
      "  \"traceId\":\"EFpFOL\"," +
      "  \"version\":\"MAESTRO\"" +
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
}
