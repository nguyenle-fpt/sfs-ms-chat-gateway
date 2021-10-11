package com.symphony.sfs.ms.chat.api;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.symphony.sfs.ms.chat.api.util.AbstractIntegrationTest;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.generated.model.FormattingEnum;
import com.symphony.sfs.ms.chat.generated.model.MessageId;
import com.symphony.sfs.ms.chat.generated.model.MessageInfo;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessagesRequest;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessagesResponse;
import com.symphony.sfs.ms.chat.generated.model.SendMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.SendMessageRequest;
import com.symphony.sfs.ms.chat.generated.model.SendMessageResponse;
import com.symphony.sfs.ms.chat.generated.model.SendSystemMessageRequest;
import com.symphony.sfs.ms.chat.generated.model.SymphonyAttachment;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.FederatedAccountSessionService;
import com.symphony.sfs.ms.chat.service.MessageIOMonitor;
import com.symphony.sfs.ms.chat.service.SymphonyMessageSender;
import com.symphony.sfs.ms.chat.service.SymphonyMessageService;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyService;
import com.symphony.sfs.ms.chat.util.HttpRequestUtils;
import com.symphony.sfs.ms.starter.config.ExceptionHandling;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.config.properties.common.PemResource;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamAttributes;
import com.symphony.sfs.ms.starter.symphony.stream.StreamInfo;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.symphony.stream.StreamType;
import com.symphony.sfs.ms.starter.symphony.stream.StreamTypes;
import com.symphony.sfs.ms.starter.symphony.user.UsersInfoService;
import io.fabric8.mockwebserver.DefaultMockServer;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.zalando.problem.DefaultProblem;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static com.symphony.sfs.ms.chat.generated.api.MessagingApi.RETRIEVEMESSAGES_ENDPOINT;
import static com.symphony.sfs.ms.chat.generated.api.MessagingApi.SENDSYSTEMMESSAGE_ENDPOINT;
import static com.symphony.sfs.ms.starter.testing.MockMvcUtils.configuredGiven;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessagingApiTest extends AbstractIntegrationTest {

  protected MessagingApi symphonyMessagingApi;
  private SymphonyMessageSender symphonyMessageSender;
  private SymphonyService symphonyService;

  private DatafeedSessionPool.DatafeedSession datafeedSession;
  private SymphonySession symphonySession;

  @BeforeEach
  public void setUp(AmazonDynamoDB db, DefaultMockServer mockServer, MessageSource messageSource) throws Exception {
    super.setUp(db, mockServer, messageSource);

    symphonyMessageSender = mock(SymphonyMessageSender.class);
    StreamService streamService = mock(StreamService.class);
    StreamInfo streamInfo = StreamInfo.builder().streamAttributes(StreamAttributes.builder().members(Arrays.asList(1L)).build()).streamType(new StreamType(StreamTypes.ROOM)).build();
    when(streamService.getStreamInfo(anyString(), any(), anyString())).thenReturn(Optional.of(streamInfo));

    BotConfiguration botConfiguration = new BotConfiguration();
    botConfiguration.setUsername("username");
    botConfiguration.setEmailAddress("emailAddress");
    botConfiguration.setPrivateKey(new PemResource("-----botConfigurationPrivateKey"));

    PodConfiguration podConfiguration = new PodConfiguration();
    podConfiguration.setUrl("podUrl");
    podConfiguration.setSessionAuth("sessionAuth");
    podConfiguration.setKeyAuth("keyAuth");

    FederatedAccountRepository federatedAccountRepository = mock(FederatedAccountRepository.class);
    FederatedAccount federatedAccount = FederatedAccount.builder().symphonyUsername("username").symphonyUserId("fromSymphonyUserId").build();
    when(federatedAccountRepository.findBySymphonyId(anyString())).thenReturn(Optional.of(federatedAccount));

    AuthenticationService authenticationService = mock(AuthenticationService.class);
    symphonySession = new SymphonySession();
    symphonySession.setSessionToken("sessionToken");
    symphonySession.setUsername("username");
    symphonySession.setKmToken("kmToken");
    datafeedSession = new DatafeedSessionPool.DatafeedSession(symphonySession, "fromSymphonyUserId");
    when(authenticationService.authenticate(anyString(), anyString(), anyString(), anyString())).thenReturn(symphonySession);

    AdminClient adminClient = mock(AdminClient.class);
    EmpClient empClient = mock(EmpClient.class);
    UsersInfoService usersInfoService = mock(UsersInfoService.class);

    FederatedAccountSessionService federatedAccountSessionService = new FederatedAccountSessionService(federatedAccountRepository);
    DatafeedSessionPool datafeedSessionPool = new DatafeedSessionPool(authenticationService, podConfiguration, chatConfiguration, federatedAccountSessionService, meterManager);

    symphonyService = mock(SymphonyService.class);

    SymphonyMessageService symphonyMessageService = new SymphonyMessageService(empClient, federatedAccountRepository, forwarderQueueConsumer, datafeedSessionPool, symphonyMessageSender, adminClient, null, symphonyService, podConfiguration, botConfiguration, authenticationService, streamService, new MessageIOMonitor(meterManager), messageSource);
    symphonyMessagingApi = new MessagingApi(symphonyMessageService);
  }

  @Test
  void sendMessage() {
    SendMessageRequest sendMessageRequest = createTestMessage("streamId", "fromSymphonyUserId", "text", null);
    verifyRequest(sendMessageRequest, HttpStatus.NO_CONTENT, Void.class);
  }

  @Test
  void sendFormattedMessage() {
    SendMessageRequest sendMessageRequest = createTestMessage("streamId", "fromSymphonyUserId", "text", FormattingEnum.INFO);
    verifyRequest(sendMessageRequest, HttpStatus.NO_CONTENT, Void.class);
  }

  @Test
  void sendMessage_Error() {
    SendMessageRequest sendMessageRequest = createTestMessage("streamId", "wrongSymphonyUserId", "text", null);
    doThrow(new SendMessageFailedProblem()).when(symphonyMessageSender).sendRawMessage("streamId", "wrongSymphonyUserId", "<messageML>text</messageML>", "toSymphonyUserId");
    DefaultProblem response = verifyRequest(sendMessageRequest, HttpStatus.BAD_REQUEST, DefaultProblem.class);
  }

  @Test
  void sendMessageToRoom_noAttachments_noFormatting() {
    SendMessageRequest sendMessageRequest = new SendMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text");
    when(symphonyMessageSender.sendRawMessage("streamId", "fromSymphonyUserId", "<messageML>text</messageML>", null)).thenReturn(Optional.of("symphonyMessageId"));
    SendMessageResponse sendMessageResponse = sendMessage(sendMessageRequest);
    SendMessageResponse expectedResponse = new SendMessageResponse().id("symphonyMessageId");

    assertEquals(expectedResponse, sendMessageResponse);
  }

  @Test
  void sendMessageToRoom_noAttachments_simpleFormatting() {
    SendMessageRequest sendMessageRequest = new SendMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text").formatting(FormattingEnum.SIMPLE);
    when(symphonyMessageSender.sendSimpleMessage("streamId", "fromSymphonyUserId", "text", null)).thenReturn(Optional.of("symphonyMessageId"));
    SendMessageResponse sendMessageResponse = sendMessage(sendMessageRequest);
    SendMessageResponse expectedResponse = new SendMessageResponse().id("symphonyMessageId");

    assertEquals(expectedResponse, sendMessageResponse);
  }

  @Test
  void sendMessageToRoom_noAttachments_notificationFormatting() {
    SendMessageRequest sendMessageRequest = new SendMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text").formatting(FormattingEnum.NOTIFICATION);
    when(symphonyMessageSender.sendNotificationMessage("streamId", "fromSymphonyUserId", "text", null)).thenReturn(Optional.of("symphonyMessageId"));
    SendMessageResponse sendMessageResponse = sendMessage(sendMessageRequest);
    SendMessageResponse expectedResponse = new SendMessageResponse().id("symphonyMessageId");

    assertEquals(expectedResponse, sendMessageResponse);
  }

  @Test
  void sendMessageToRoom_noAttachments_alertFormatting() {
    SendMessageRequest sendMessageRequest = new SendMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text").formatting(FormattingEnum.ALERT);
    when(symphonyMessageSender.sendAlertMessage("streamId", "fromSymphonyUserId", "text", null)).thenReturn(Optional.of("symphonyMessageId"));
    SendMessageResponse sendMessageResponse = sendMessage(sendMessageRequest);
    SendMessageResponse expectedResponse = new SendMessageResponse().id("symphonyMessageId");

    assertEquals(expectedResponse, sendMessageResponse);
  }

  @Test
  void sendMessageToRoom_noAttachments_infoFormatting() {
    SendMessageRequest sendMessageRequest = new SendMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text").formatting(FormattingEnum.INFO);
    when(symphonyMessageSender.sendInfoMessage("streamId", "fromSymphonyUserId", "text", null)).thenReturn(Optional.of("symphonyMessageId"));
    SendMessageResponse sendMessageResponse = sendMessage(sendMessageRequest);
    SendMessageResponse expectedResponse = new SendMessageResponse().id("symphonyMessageId");

    assertEquals(expectedResponse, sendMessageResponse);
  }

  @Test
  void sendMessageToRoom_withAttachments() {
    SymphonyAttachment attachment = new SymphonyAttachment().contentType("image/png").data(Base64.encodeBase64String("image".getBytes(StandardCharsets.UTF_8))).fileName("attachment.png");
    SendMessageRequest sendMessageRequest = new SendMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text").addAttachmentsItem(attachment);
    when(symphonyMessageSender.sendRawMessageWithAttachments("streamId", "fromSymphonyUserId", "<messageML>text</messageML>", null, Collections.singletonList(attachment))).thenReturn(Optional.of("symphonyMessageId"));
    SendMessageResponse sendMessageResponse = sendMessage(sendMessageRequest);
    SendMessageResponse expectedResponse = new SendMessageResponse().id("symphonyMessageId");

    assertEquals(expectedResponse, sendMessageResponse);
  }

  @Test
  void sendSystemMessageToRoom_noFormatting() {
    SendSystemMessageRequest sendsystemMessageRequest = new SendSystemMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text");
    when(symphonyMessageSender.sendRawMessage(symphonySession, "streamId", "<messageML>text</messageML>")).thenReturn(Optional.of("symphonyMessageId"));
    SendMessageResponse sendSystemMessageResponse = sendSystemMessage(sendsystemMessageRequest);
    SendMessageResponse expectedResponse = new SendMessageResponse().id("symphonyMessageId");

    assertEquals(expectedResponse, sendSystemMessageResponse);
  }

  @Test
  void sendSystemMessageToRoom_simpleFormatting() {
    SendSystemMessageRequest sendsystemMessageRequest = new SendSystemMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text").formatting(FormattingEnum.SIMPLE);
    when(symphonyMessageSender.sendSimpleMessage(symphonySession, "streamId", "text")).thenReturn(Optional.of("symphonyMessageId"));
    SendMessageResponse sendSystemMessageResponse = sendSystemMessage(sendsystemMessageRequest);
    SendMessageResponse expectedResponse = new SendMessageResponse().id("symphonyMessageId");

    assertEquals(expectedResponse, sendSystemMessageResponse);
  }

  @Test
  void sendSystemMessageToRoom_notificationFormatting() {
    SendSystemMessageRequest sendsystemMessageRequest = new SendSystemMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text").formatting(FormattingEnum.NOTIFICATION);
    when(symphonyMessageSender.sendNotificationMessage(symphonySession, "streamId", "text")).thenReturn(Optional.of("symphonyMessageId"));
    SendMessageResponse sendSystemMessageResponse = sendSystemMessage(sendsystemMessageRequest);
    SendMessageResponse expectedResponse = new SendMessageResponse().id("symphonyMessageId");

    assertEquals(expectedResponse, sendSystemMessageResponse);
  }

  @Test
  void sendSystemMessageToRoom_alertFormatting() {
    SendSystemMessageRequest sendsystemMessageRequest = new SendSystemMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text").title("title").formatting(FormattingEnum.ALERT);
    when(symphonyMessageSender.sendAlertMessage(symphonySession, "streamId", "text", "title", Collections.emptyList())).thenReturn(Optional.of("symphonyMessageId"));
    SendMessageResponse sendSystemMessageResponse = sendSystemMessage(sendsystemMessageRequest);
    SendMessageResponse expectedResponse = new SendMessageResponse().id("symphonyMessageId");

    assertEquals(expectedResponse, sendSystemMessageResponse);
  }

  @Test
  void sendSystemMessageToRoom_infoFormatting() {
    SendSystemMessageRequest sendsystemMessageRequest = new SendSystemMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text").formatting(FormattingEnum.INFO);
    when(symphonyMessageSender.sendInfoMessage(symphonySession, "streamId", "text")).thenReturn(Optional.of("symphonyMessageId"));
    SendMessageResponse sendSystemMessageResponse = sendSystemMessage(sendsystemMessageRequest);
    SendMessageResponse expectedResponse = new SendMessageResponse().id("symphonyMessageId");

    assertEquals(expectedResponse, sendSystemMessageResponse);
  }

  @Test
  void retrieveMessages_ok() {
    RetrieveMessagesRequest retrieveMessagesRequest = new RetrieveMessagesRequest().messagesIds(Arrays.asList(new MessageId().messageId("messageId1"), new MessageId().messageId("messageId2"))).symphonyUserId("fromSymphonyUserId");

    when(symphonyService.getMessage("messageId1", datafeedSession)).thenReturn(Optional.of(new MessageInfo().messageId("messageId1").disclaimer("disclaimer1").message("This is the message 1")));
    when(symphonyService.getMessage("messageId2", datafeedSession)).thenReturn(Optional.of(new MessageInfo().messageId("messageId2").disclaimer("disclaimer2").message("This is the message 2")));

    RetrieveMessagesResponse response = retrieveMessages(retrieveMessagesRequest);
    RetrieveMessagesResponse expectedResponse = new RetrieveMessagesResponse().messages(Arrays.asList(
      new MessageInfo().messageId("messageId1").disclaimer("disclaimer1").message("This is the message 1"),
      new MessageInfo().messageId("messageId2").disclaimer("disclaimer2").message("This is the message 2"))
    );

    assertEquals(expectedResponse, response);
  }

  @Test
  void retrieveMessages_ok_specialCharacters() {
    RetrieveMessagesRequest retrieveMessagesRequest = new RetrieveMessagesRequest().messagesIds(Collections.singletonList(new MessageId().messageId("messageId1"))).symphonyUserId("fromSymphonyUserId");

    when(symphonyService.getMessage("messageId1", datafeedSession)).thenReturn(Optional.of(new MessageInfo().messageId("messageId1").disclaimer("disclaimer1").message("This \\+ is \\_the\\_message \\*1")));

    RetrieveMessagesResponse response = retrieveMessages(retrieveMessagesRequest);
    RetrieveMessagesResponse expectedResponse = new RetrieveMessagesResponse().messages(Collections.singletonList(new MessageInfo().messageId("messageId1").disclaimer("disclaimer1").message("This + is _the_message *1")));

    assertEquals(expectedResponse, response);
  }

  @Test
  void retrieveMessages_fails() {
    RetrieveMessagesRequest retrieveMessagesRequest = new RetrieveMessagesRequest().messagesIds(Arrays.asList(new MessageId().messageId("messageId1"), new MessageId().messageId("messageId2"), new MessageId().messageId("messageId3"))).symphonyUserId("fromSymphonyUserId");

    when(symphonyService.getMessage("messageId1", datafeedSession)).thenReturn(Optional.of(new MessageInfo().messageId("messageId1").disclaimer("disclaimer1").message("This is the message 1")));
    when(symphonyService.getMessage("messageId2", datafeedSession)).thenReturn(Optional.of(new MessageInfo().messageId("messageId2").disclaimer("disclaimer2").message("This is the message 2")));

    HttpRequestUtils.getRequestFail(symphonyMessagingApi, retrieveMessagesRequest, RETRIEVEMESSAGES_ENDPOINT, Collections.emptyList(), objectMapper, tracer, RetrieveMessageFailedProblem.class.getName(), null, HttpStatus.BAD_REQUEST);
  }

  private <RESPONSE> RESPONSE verifyRequest(SendMessageRequest sendMessageRequest, HttpStatus expectedStatus, Class<RESPONSE> response) {
//    return configuredGiven(objectMapper, new ExceptionHandling(tracer), symphonyMessagingApi)
//      .contentType(MediaType.APPLICATION_JSON_VALUE)
//      .body(sendMessageRequest)
//      .when()
//      .post(SENDMESSAGE_ENDPOINT)
//      .then()
//      .statusCode(expectedStatus.value())
//      .extract().response().body()
//      .as(response);
    return null;
  }

  private RetrieveMessagesResponse retrieveMessages(RetrieveMessagesRequest retrieveMessagesRequest) {
    return configuredGiven(objectMapper, new ExceptionHandling(tracer), symphonyMessagingApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(retrieveMessagesRequest)
      .when()
      .get(RETRIEVEMESSAGES_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value())
      .extract().response().body()
      .as(RetrieveMessagesResponse.class);
  }

  private SendMessageResponse sendMessage(SendMessageRequest sendMessageRequest) {
    return configuredGiven(objectMapper, new ExceptionHandling(tracer), symphonyMessagingApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(sendMessageRequest)
      .when()
      .post(RETRIEVEMESSAGES_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value())
      .extract().response().body()
      .as(SendMessageResponse.class);
  }

  private SendMessageResponse sendSystemMessage(SendSystemMessageRequest sendSystemMessageRequest) {
    return configuredGiven(objectMapper, new ExceptionHandling(tracer), symphonyMessagingApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(sendSystemMessageRequest)
      .when()
      .post(SENDSYSTEMMESSAGE_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value())
      .extract().response().body()
      .as(SendMessageResponse.class);
  }

  private SendMessageRequest createTestMessage(String streamId, String fromSymphonyUserId, String text, FormattingEnum formatting) {
    SendMessageRequest sendMessageRequest = new SendMessageRequest();
    sendMessageRequest.setStreamId(streamId);
    sendMessageRequest.setFromSymphonyUserId(fromSymphonyUserId);
    sendMessageRequest.setFormatting(formatting);
    sendMessageRequest.setText(text);
    return sendMessageRequest;
  }
}
