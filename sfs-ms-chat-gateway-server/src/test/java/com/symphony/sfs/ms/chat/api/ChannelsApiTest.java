package com.symphony.sfs.ms.chat.api;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.symphony.sfs.ms.admin.generated.model.CanChatResponse;
import com.symphony.sfs.ms.chat.api.util.AbstractIntegrationTest;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.generated.model.CreateChannelRequest;
import com.symphony.sfs.ms.chat.generated.model.DeleteChannelRequest;
import com.symphony.sfs.ms.chat.generated.model.DeleteChannelResponse;
import com.symphony.sfs.ms.chat.generated.model.DeleteChannelsRequest;
import com.symphony.sfs.ms.chat.generated.model.DeleteChannelsResponse;
import com.symphony.sfs.ms.chat.model.Channel;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.service.EmpSchemaService;
import com.symphony.sfs.ms.chat.service.FederatedAccountService;
import com.symphony.sfs.ms.chat.service.external.MockAdminClient;
import com.symphony.sfs.ms.chat.service.external.MockEmpClient;
import com.symphony.sfs.ms.starter.config.ExceptionHandling;
import com.symphony.sfs.ms.starter.security.SessionManager;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.StringId;
import com.symphony.sfs.ms.starter.symphony.user.AdminUserManagementService;
import com.symphony.sfs.ms.starter.symphony.user.UsersInfoService;
import com.symphony.sfs.ms.starter.symphony.xpod.ConnectionRequestStatus;
import com.symphony.sfs.ms.starter.util.BulkRemovalStatus;
import io.fabric8.mockwebserver.DefaultMockServer;
import io.opentracing.Tracer;
import model.InboundConnectionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static clients.symphony.api.constants.PodConstants.GETCONNECTIONSTATUS;
import static clients.symphony.api.constants.PodConstants.GETIM;
import static clients.symphony.api.constants.PodConstants.SENDCONNECTIONREQUEST;
import static com.symphony.sfs.ms.chat.api.util.SnsMessageUtil.getAcceptedConnectionRequestMaestroMessage;
import static com.symphony.sfs.ms.chat.api.util.SnsMessageUtil.getEnvelopeMessage;
import static com.symphony.sfs.ms.chat.api.util.SnsMessageUtil.getSnsMaestroMessage;
import static com.symphony.sfs.ms.chat.generated.api.ChannelsApi.CREATECHANNEL_ENDPOINT;
import static com.symphony.sfs.ms.chat.generated.api.ChannelsApi.DELETECHANNELS_ENDPOINT;
import static com.symphony.sfs.ms.chat.generated.api.ChannelsApi.RETRIEVECHANNEL_ENDPOINT;
import static com.symphony.sfs.ms.starter.testing.MockMvcUtils.configuredGiven;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class ChannelsApiTest extends AbstractIntegrationTest {
  protected ChannelsApi channelApi;
  protected AccountsApi accountsApi;
  protected FederatedAccountService federatedAccountService;
  protected EmpSchemaService empSchemaService;
  protected MockAdminClient mockAdminClient;
  private Tracer tracer = null;

  @BeforeEach
  public void setUp(AmazonDynamoDB db, DefaultMockServer mockServer) throws Exception {
    super.setUp(db, mockServer);
    empSchemaService = mock(EmpSchemaService.class);

    SessionManager sessionManager = new SessionManager(webClient, Collections.emptyList());
    mockAdminClient = new MockAdminClient();

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
      mockAdminClient,
      channelRepository);
    federatedAccountService.registerAsDatafeedListener();
    channelApi = new ChannelsApi(federatedAccountService, channelService);
    accountsApi = new AccountsApi(federatedAccountService);
  }

  private FederatedAccount createAndSaveFederatedAccount(String federatedUserId,  String username, String symphonyUserId, String emp){
    FederatedAccount account = FederatedAccount.builder()
      .emailAddress("emailAddress@symphony.com")
      .phoneNumber("+33601020304")
      .firstName("firstName")
      .lastName("lastName")
      .companyName("companyName")
      .federatedUserId(federatedUserId)
      .emp(emp)
      .symphonyUserId(symphonyUserId)
      .symphonyUsername(username)
      .build();
    federatedAccountRepository.save(account);
    return account;
  }

  private void createChannel(String federatedUserId, String emp) {
    CreateChannelRequest createChannelRequest = new CreateChannelRequest()
      .federatedUserId(federatedUserId)
      .advisorUserId("99")
      .emp(emp);

    configuredGiven(objectMapper, new ExceptionHandling(tracer), channelApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(createChannelRequest)
      .when()
      .post(CREATECHANNEL_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value());
  }

  private String createNotification(FederatedAccount account, DatafeedSessionPool.DatafeedSession accountSession){
    return getSnsMaestroMessage("196", getEnvelopeMessage(getAcceptedConnectionRequestMaestroMessage(
      FederatedAccount.builder()
        .emailAddress(account.getEmailAddress())
        .phoneNumber(account.getPhoneNumber())
        .firstName(account.getFirstName())
        .lastName(account.getLastName())
        .symphonyUserId(accountSession.getUserId())
        .symphonyUsername(accountSession.getUsername())
        .build(),
      FederatedAccount.builder()
        .symphonyUserId("2")
        .build()
    )));
  }

  @Test
  public void deleteChannelTest() throws IOException {
    SymphonySession botSession = getSession(botConfiguration.getUsername());
    DatafeedSessionPool.DatafeedSession accountSession1 = new DatafeedSessionPool.DatafeedSession(getSession("username1"), "1");
    DatafeedSessionPool.DatafeedSession accountSession2 = new DatafeedSessionPool.DatafeedSession(getSession("username2"), "2");
    DatafeedSessionPool.DatafeedSession accountSession3 = new DatafeedSessionPool.DatafeedSession(getSession("username3"), "3");
    DatafeedSessionPool.DatafeedSession accountSession4 = new DatafeedSessionPool.DatafeedSession(getSession("username4"), "4");

    when(authenticationService.authenticate(any(), any(), eq(botConfiguration.getUsername()), anyString())).thenReturn(botSession);
    when(authenticationService.authenticate(any(), any(), eq(accountSession1.getUsername()), anyString())).thenReturn(accountSession1);
    when(authenticationService.authenticate(any(), any(), eq(accountSession2.getUsername()), anyString())).thenReturn(accountSession2);
    when(authenticationService.authenticate(any(), any(), eq(accountSession3.getUsername()), anyString())).thenReturn(accountSession3);
    when(authenticationService.authenticate(any(), any(), eq(accountSession4.getUsername()), anyString())).thenReturn(accountSession4);


    mockServer.expect()
      .get()
      .withPath(GETCONNECTIONSTATUS.replace("{userId}", "99"))
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

    FederatedAccount existingAccount1 = this.createAndSaveFederatedAccount("federatedUserId1", "username1", "1", "WHATSAPP");
    FederatedAccount existingAccount2 = this.createAndSaveFederatedAccount("federatedUserId2", "username2", "2", "WHATSAPP");
    FederatedAccount existingAccount3 = this.createAndSaveFederatedAccount("federatedUserId3", "username3", "3", "WHATSAPPGROUPS");
    FederatedAccount existingAccount4 = this.createAndSaveFederatedAccount("federatedUserId4", "username4", "4", "WHATSAPP");

    assertEquals(0, ((MockEmpClient) empClient).getChannels().size());


    this.createChannel(existingAccount1.getFederatedUserId(), existingAccount1.getEmp());
    this.createChannel(existingAccount2.getFederatedUserId(), existingAccount2.getEmp());
    this.createChannel(existingAccount3.getFederatedUserId(), existingAccount3.getEmp());
    this.createChannel(existingAccount4.getFederatedUserId(), existingAccount4.getEmp());

    mockAdminClient.setCanChatResponse(Optional.of(CanChatResponse.CAN_CHAT));
    forwarderQueueConsumer.consume(this.createNotification(existingAccount1, accountSession1), "1");
    forwarderQueueConsumer.consume(this.createNotification(existingAccount2, accountSession2), "2");
    forwarderQueueConsumer.consume(this.createNotification(existingAccount3, accountSession3), "3");
    forwarderQueueConsumer.consume(this.createNotification(existingAccount4, accountSession4), "4");
    assertEquals(2, ((MockEmpClient) empClient).getChannels().size());
    this.setStreamID("2", "federatedUserId1", "WHATSAPP", "streamID1");
    this.setStreamID("2", "federatedUserId2", "WHATSAPP", "streamID2");
    this.setStreamID("2", "federatedUserId3", "WHATSAPPGROUPS", "streamID3");
    this.setStreamID("2", "federatedUserId4", "WHATSAPP", "streamID4_failure");
      empClient.deleteChannels(List.of("streamId"), "WHATSAPPGROUPS");
      empClient.deleteChannels(List.of("streamId"), "WHATSAPP");

    DeleteChannelRequest request1 = new DeleteChannelRequest().advisorSymphonyId("2").federatedUserId("federatedUserId1").entitlementType("WHATSAPP");
    DeleteChannelRequest request2 = new DeleteChannelRequest().advisorSymphonyId("2").federatedUserId("federatedUserId2").entitlementType("WHATSAPP");
    DeleteChannelRequest request3 = new DeleteChannelRequest().advisorSymphonyId("2").federatedUserId("federatedUserId3").entitlementType("WHATSAPPGROUPS");
    DeleteChannelRequest request4 = new DeleteChannelRequest().advisorSymphonyId("2").federatedUserId("blabla").entitlementType("WHATSAPPGROUPS");
    DeleteChannelRequest request5 = new DeleteChannelRequest().advisorSymphonyId("2").federatedUserId("federatedUserId4").entitlementType("WHATSAPP");
    DeleteChannelsRequest deleteChannelsRequest = new DeleteChannelsRequest().channels(List.of(request1, request2, request3, request4, request5));

    assertTrue(channelRepository.findByAdvisorSymphonyIdAndFederatedUserIdAndEmp("2", existingAccount1.getFederatedUserId(), existingAccount1.getEmp()).isPresent());
    assertTrue(channelRepository.findByAdvisorSymphonyIdAndFederatedUserIdAndEmp("2", existingAccount2.getFederatedUserId(), existingAccount2.getEmp()).isPresent());
    assertTrue(channelRepository.findByAdvisorSymphonyIdAndFederatedUserIdAndEmp("2", existingAccount3.getFederatedUserId(), existingAccount3.getEmp()).isPresent());
    assertTrue(channelRepository.findByAdvisorSymphonyIdAndFederatedUserIdAndEmp("2", existingAccount4.getFederatedUserId(), existingAccount4.getEmp()).isPresent());

    DeleteChannelsResponse result = configuredGiven(objectMapper, new ExceptionHandling(tracer), channelApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(deleteChannelsRequest)
      .when()
      .post(DELETECHANNELS_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value())
      .extract().response().body()
      .as(DeleteChannelsResponse.class);

    assertNotNull(result);
    assertEquals(5, result.getReport().size());
    this.testsStatusMatches(result, BulkRemovalStatus.SUCCESS, request1);
    this.testsStatusMatches(result, BulkRemovalStatus.SUCCESS, request2);
    this.testsStatusMatches(result, BulkRemovalStatus.SUCCESS, request3);
    this.testsStatusMatches(result, BulkRemovalStatus.NOT_FOUND, request4);
    this.testsStatusMatches(result, BulkRemovalStatus.FAILURE, request5);


    assertEquals(1, ((MockEmpClient) empClient).getChannels().size());
    assertTrue(channelRepository.findByAdvisorSymphonyIdAndFederatedUserIdAndEmp("2", existingAccount1.getFederatedUserId(), existingAccount1.getEmp()).isEmpty());
    assertTrue(channelRepository.findByAdvisorSymphonyIdAndFederatedUserIdAndEmp("2", existingAccount2.getFederatedUserId(), existingAccount2.getEmp()).isEmpty());
    assertTrue(channelRepository.findByAdvisorSymphonyIdAndFederatedUserIdAndEmp("2", existingAccount3.getFederatedUserId(), existingAccount3.getEmp()).isEmpty());
    assertTrue(channelRepository.findByAdvisorSymphonyIdAndFederatedUserIdAndEmp("2", existingAccount4.getFederatedUserId(), existingAccount4.getEmp()).isPresent());
  }

  // a hack to set the streamID that we want
  private void setStreamID(String advisorSymphonyId, String federatedUserId, String emp, String streamID) {
    Channel channel = channelRepository.findByAdvisorSymphonyIdAndFederatedUserIdAndEmp(advisorSymphonyId, federatedUserId, emp).get();
    channel.setStreamId(streamID);
    channelRepository.save(channel);
    empClient.createChannel(emp, streamID, null, null, null);
  }

  private void testsStatusMatches(DeleteChannelsResponse result, BulkRemovalStatus status, DeleteChannelRequest expected) {
    Optional<DeleteChannelResponse> actual = result.getReport().stream().filter(r -> r.getChannel().equals(expected)).findAny();
    assertTrue(actual.isPresent());
    assertEquals(status, actual.get().getStatus());
  }


  @Test
  public void retrieveChannelNotFoundTest() {
    String advisorId = "";
    String federatedUserId = "";
    String emp = "";
    configuredGiven(objectMapper, new ExceptionHandling(tracer), channelApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .when()
      .delete(RETRIEVECHANNEL_ENDPOINT, advisorId, federatedUserId, emp)
      .then()
      .statusCode(HttpStatus.NOT_FOUND.value());
  }

  @Test
  public void retrieveChannelTest() throws IOException {
    SymphonySession botSession = getSession(botConfiguration.getUsername());
    DatafeedSessionPool.DatafeedSession accountSession = new DatafeedSessionPool.DatafeedSession(getSession("username"), "1");

    when(authenticationService.authenticate(any(), any(), eq(botConfiguration.getUsername()), anyString())).thenReturn(botSession);
    when(authenticationService.authenticate(any(), any(), eq(accountSession.getUsername()), anyString())).thenReturn(accountSession);

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

    FederatedAccount existingAccount = FederatedAccount.builder()
      .emailAddress("emailAddress@symphony.com")
      .phoneNumber("+33601020304")
      .firstName("firstName")
      .lastName("lastName")
      .companyName("companyName")
      .federatedUserId("federatedUserId")
      .emp("WHATSAPP")
      .symphonyUserId("1")
      .symphonyUsername("username")
      .build();
    federatedAccountRepository.save(existingAccount);

    assertEquals(0, ((MockEmpClient) empClient).getChannels().size());

    String notification = getSnsMaestroMessage("196", getEnvelopeMessage(getAcceptedConnectionRequestMaestroMessage(
      FederatedAccount.builder()
        .emailAddress(existingAccount.getEmailAddress())
        .phoneNumber(existingAccount.getPhoneNumber())
        .firstName(existingAccount.getFirstName())
        .lastName(existingAccount.getLastName())
        .symphonyUserId(accountSession.getUserId())
        .symphonyUsername(accountSession.getUsername())
        .build(),
      FederatedAccount.builder()
        .symphonyUserId("2")
        .build()
    )));
    CreateChannelRequest createChannelRequest = new CreateChannelRequest()
      .federatedUserId(existingAccount.getFederatedUserId())
      .advisorUserId("2")
      .emp("WHATSAPP");

    configuredGiven(objectMapper, new ExceptionHandling(tracer), channelApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(createChannelRequest)
      .when()
      .post(CREATECHANNEL_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value());

    mockAdminClient.setCanChatResponse(Optional.of(CanChatResponse.CAN_CHAT));
    forwarderQueueConsumer.consume(notification, "1");
    assertEquals(1, ((MockEmpClient) empClient).getChannels().size());
    assertTrue(channelRepository.findByAdvisorSymphonyIdAndFederatedUserIdAndEmp("2", existingAccount.getFederatedUserId(), existingAccount.getEmp()).isPresent());

    configuredGiven(objectMapper, new ExceptionHandling(tracer), channelApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .when()
      .get(RETRIEVECHANNEL_ENDPOINT, "2", "federatedUserId", "WHATSAPP")
      .then()
      .statusCode(HttpStatus.OK.value());
  }
}
