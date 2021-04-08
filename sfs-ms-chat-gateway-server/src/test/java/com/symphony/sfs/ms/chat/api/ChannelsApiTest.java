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
import com.symphony.sfs.ms.emp.generated.model.ChannelIdentifier;
import com.symphony.sfs.ms.starter.config.ExceptionHandling;
import com.symphony.sfs.ms.starter.security.SessionManager;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.StringId;
import com.symphony.sfs.ms.starter.symphony.tds.TenantDetailEntity;
import com.symphony.sfs.ms.starter.symphony.tds.TenantSettings;
import com.symphony.sfs.ms.starter.symphony.user.AdminUserManagementService;
import com.symphony.sfs.ms.starter.symphony.user.UsersInfoService;
import com.symphony.sfs.ms.starter.symphony.xpod.ConnectionRequestStatus;
import com.symphony.sfs.ms.starter.util.BulkRemovalStatus;
import io.fabric8.mockwebserver.DefaultMockServer;
import io.opentracing.Tracer;
import model.InboundConnectionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class ChannelsApiTest extends AbstractIntegrationTest {
  protected ChannelsApi channelApi;
  protected AccountsApi accountsApi;
  protected FederatedAccountService federatedAccountService;
  protected EmpSchemaService empSchemaService;
  protected MockAdminClient mockAdminClient;
  private Tracer tracer = null;

  @BeforeEach
  public void setUp(AmazonDynamoDB db, DefaultMockServer mockServer, MessageSource messageSource) throws Exception {
    super.setUp(db, mockServer, messageSource);
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
      channelRepository,
      podVersionChecker,
      tenantDetailRepository);
    federatedAccountService.registerAsDatafeedListener();
    channelApi = new ChannelsApi(federatedAccountService, channelService);
    accountsApi = new AccountsApi(federatedAccountService);

    botSession = new SymphonySession("username", "kmToken", "sessionToken");
    when(authenticationService.authenticate(anyString(), anyString(), anyString(), anyString())).thenReturn(botSession);
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
    DatafeedSessionPool.DatafeedSession accountSession = initDataFeedSession();
    FederatedAccount existingAccount = prepareRetrieveChannel(accountSession);

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
    verify(empClient, times(1)).createChannel(any(), any(), any(), any(), any());
    assertEquals(1, ((MockEmpClient) empClient).getChannels().size());
    assertEquals(1, adminClient.getImRequests().size());
   }

  @Test
  public void retrieveChannelDoNotCreateChannelTest() throws IOException {
    DatafeedSessionPool.DatafeedSession accountSession = initDataFeedSession();
    FederatedAccount existingAccount = prepareRetrieveChannel(accountSession);

    // let say tenant config wants us to check the pod version
    Optional<TenantDetailEntity> optTenant = tenantDetailRepository.findByPodId("0");
    assertTrue(optTenant.isPresent());
    TenantDetailEntity tenantDetailEntity = optTenant.get();
    tenantDetailEntity.setImCreationSetting(TenantSettings.ImCreation.ACCORDING_TO_POD_VERSION.name());
    tenantDetailRepository.save(tenantDetailEntity);

    // let's make the minimal pod version (to stop creating IM) lower
    chatConfiguration.setStopImCreationAt("1.0.0");

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
    verify(empClient, times(0)).createChannel(any(), any(), any(), any(), any());
    assertEquals(0, ((MockEmpClient) empClient).getChannels().size());
    assertEquals(0, adminClient.getImRequests().size());
  }

  private DatafeedSessionPool.DatafeedSession initDataFeedSession(){
    return new DatafeedSessionPool.DatafeedSession(getSession("username"), "1");
  }

  private FederatedAccount prepareRetrieveChannel(DatafeedSessionPool.DatafeedSession accountSession){
    SymphonySession botSession = getSession(botConfiguration.getUsername());

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

    return existingAccount;
  }

  @Test
  public void deleteChannelsTest(){
    List<String> emp1Suffixes = Arrays.asList("31", "32", "33", "34");
    List<String> emp2Suffixes = Arrays.asList("41", "42");
    List<String> emp3Suffixes = Collections.singletonList("51");
    createFederatedAccounts("emp1", emp1Suffixes);
    createFederatedAccounts("emp2", emp2Suffixes);
    createFederatedAccounts("emp3", emp3Suffixes);
    String streamId = "streamId";
    DeleteChannelsRequest request = generateDeleteChannelsRequest(streamId, "emp1", "emp2","emp3", emp1Suffixes, emp2Suffixes, emp3Suffixes);

    com.symphony.sfs.ms.emp.generated.model.DeleteChannelsResponse responseEmp1 = new com.symphony.sfs.ms.emp.generated.model.DeleteChannelsResponse().report(Arrays.asList(
      new com.symphony.sfs.ms.emp.generated.model.DeleteChannelResponse().symphonyId("symphonyUserId31").streamId(streamId).status(BulkRemovalStatus.SUCCESS),
      new com.symphony.sfs.ms.emp.generated.model.DeleteChannelResponse().symphonyId("symphonyUserId32").streamId(streamId).status(BulkRemovalStatus.NOT_FOUND),
      new com.symphony.sfs.ms.emp.generated.model.DeleteChannelResponse().symphonyId("symphonyUserId33").streamId(streamId).status(BulkRemovalStatus.FAILURE),
      new com.symphony.sfs.ms.emp.generated.model.DeleteChannelResponse().symphonyId("symphonyUserId34").streamId(streamId).status(BulkRemovalStatus.SUCCESS)
    ));

    com.symphony.sfs.ms.emp.generated.model.DeleteChannelsResponse responseEmp3 = new com.symphony.sfs.ms.emp.generated.model.DeleteChannelsResponse().report(Collections.singletonList(
      new com.symphony.sfs.ms.emp.generated.model.DeleteChannelResponse().symphonyId("symphonyUserId51").streamId(streamId).status(BulkRemovalStatus.SUCCESS)
    ));

    when(empClient.deleteChannels(emp1Suffixes.stream().map(s -> new ChannelIdentifier().streamId(streamId).symphonyId("symphonyUserId" + s)).collect(Collectors.toList()), "emp1")).thenReturn(Optional.of(responseEmp1));
    when(empClient.deleteChannels(emp2Suffixes.stream().map(s -> new ChannelIdentifier().streamId(streamId).symphonyId("symphonyUserId" + s)).collect(Collectors.toList()), "emp2")).thenReturn(Optional.empty());
    when(empClient.deleteChannels(emp3Suffixes.stream().map(s -> new ChannelIdentifier().streamId(streamId).symphonyId("symphonyUserId" + s)).collect(Collectors.toList()), "emp3")).thenReturn(Optional.of(responseEmp3));

    DeleteChannelsResponse response = configuredGiven(objectMapper, new ExceptionHandling(tracer), channelApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(request)
      .when()
      .post(DELETECHANNELS_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value())
      .extract().response().body()
      .as(DeleteChannelsResponse.class);


    assertEquals(7, response.getReport().size());
    //emp1
    assertTrue(response.getReport().contains(new DeleteChannelResponse().channel(request.getChannels().get(0)).status(BulkRemovalStatus.SUCCESS)));
    assertTrue(response.getReport().contains(new DeleteChannelResponse().channel(request.getChannels().get(1)).status(BulkRemovalStatus.SUCCESS)));
    assertTrue(response.getReport().contains(new DeleteChannelResponse().channel(request.getChannels().get(2)).status(BulkRemovalStatus.FAILURE)));
    assertTrue(response.getReport().contains(new DeleteChannelResponse().channel(request.getChannels().get(3)).status(BulkRemovalStatus.SUCCESS)));
    //emp2
    assertTrue(response.getReport().contains(new DeleteChannelResponse().channel(request.getChannels().get(4)).status(BulkRemovalStatus.FAILURE)));
    assertTrue(response.getReport().contains(new DeleteChannelResponse().channel(request.getChannels().get(5)).status(BulkRemovalStatus.FAILURE)));
    //emp3
    assertTrue(response.getReport().contains(new DeleteChannelResponse().channel(request.getChannels().get(6)).status(BulkRemovalStatus.SUCCESS)));
  }

  private void createFederatedAccounts(String emp, List<String> suffixes){
    for (String suffix : suffixes) {
      FederatedAccount account = FederatedAccount.builder()
        .emailAddress("emailAddress" + suffix + "@symphony.com")
        .phoneNumber("+336010203" + suffix)
        .firstName("firstName" + suffix)
        .lastName("lastName" + suffix)
        .companyName("companyName")
        .federatedUserId("federatedUserId" + suffix)
        .emp(emp)
        .symphonyUserId("symphonyUserId" + suffix)
        .symphonyUsername("username" + suffix)
        .build();
      federatedAccountRepository.save(account);
    }
  }

  private DeleteChannelsRequest generateDeleteChannelsRequest(String streamId, String emp1, String emp2, String emp3, List<String> emp1Suffixes, List<String> emp2Suffixes, List<String> emp3Suffixes){
    List<DeleteChannelRequest> channels = new ArrayList<>();
    emp1Suffixes.forEach(suffix -> channels.add(new DeleteChannelRequest().entitlementType(emp1).federatedSymphonyId("symphonyUserId" + suffix).streamId(streamId)));
    emp2Suffixes.forEach(suffix -> channels.add(new DeleteChannelRequest().entitlementType(emp2).federatedSymphonyId("symphonyUserId" + suffix).streamId(streamId)));
    emp3Suffixes.forEach(suffix -> channels.add(new DeleteChannelRequest().entitlementType(emp3).federatedSymphonyId("symphonyUserId" + suffix).streamId(streamId)));
    return new DeleteChannelsRequest().channels(channels);
  }
}
