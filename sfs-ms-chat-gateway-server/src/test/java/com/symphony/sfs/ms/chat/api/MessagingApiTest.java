package com.symphony.sfs.ms.chat.api;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.symphony.sfs.ms.admin.generated.model.BlockedFileTypes;
import com.symphony.sfs.ms.chat.api.util.AbstractIntegrationTest;
import com.symphony.sfs.ms.chat.config.EmpConfig;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.MessageDecryptor;
import com.symphony.sfs.ms.chat.generated.model.AttachmentInfo;
import com.symphony.sfs.ms.chat.generated.model.FormattingEnum;
import com.symphony.sfs.ms.chat.generated.model.MessageId;
import com.symphony.sfs.ms.chat.generated.model.MessageInfo;
import com.symphony.sfs.ms.chat.generated.model.MessageInfoWithCustomEntities;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessagesRequest;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessagesResponse;
import com.symphony.sfs.ms.chat.generated.model.SendMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.SendMessageRequest;
import com.symphony.sfs.ms.chat.generated.model.SendSystemMessageRequest;
import com.symphony.sfs.ms.chat.generated.model.SetMessagesAsReadRequest;
import com.symphony.sfs.ms.chat.generated.model.SymphonyAttachment;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.FederatedAccountSessionService;
import com.symphony.sfs.ms.chat.service.MessageIOMonitor;
import com.symphony.sfs.ms.chat.service.SymphonyMessageSender;
import com.symphony.sfs.ms.chat.service.SymphonyMessageService;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.chat.service.external.MockAdminClient;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyService;
import com.symphony.sfs.ms.chat.util.HttpRequestUtils;
import com.symphony.sfs.ms.starter.config.ExceptionHandling;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.security.SessionManager;
import com.symphony.sfs.ms.starter.security.SessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonyRsaAuthFunction;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.message.MessageStatusService;
import com.symphony.sfs.ms.starter.symphony.message.SendMessageStatusRequest;
import com.symphony.sfs.ms.starter.symphony.stream.CustomEntity;
import com.symphony.sfs.ms.starter.symphony.stream.EventUser;
import com.symphony.sfs.ms.starter.symphony.stream.MessageAttachment;
import com.symphony.sfs.ms.starter.symphony.stream.MessageEnvelope;
import com.symphony.sfs.ms.starter.symphony.stream.SBEEventMessage;
import com.symphony.sfs.ms.starter.symphony.stream.StreamAttributes;
import com.symphony.sfs.ms.starter.symphony.stream.StreamInfo;
import com.symphony.sfs.ms.starter.symphony.stream.StreamType;
import com.symphony.sfs.ms.starter.symphony.stream.StreamTypes;
import com.symphony.sfs.ms.starter.symphony.stream.ThreadMessagesResponse;
import io.fabric8.mockwebserver.DefaultMockServer;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.zalando.problem.DefaultProblem;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.symphony.sfs.ms.chat.generated.api.MessagingApi.MARKMESSAGESASREAD_ENDPOINT;
import static com.symphony.sfs.ms.chat.generated.api.MessagingApi.RETRIEVEMESSAGES_ENDPOINT;
import static com.symphony.sfs.ms.chat.generated.api.MessagingApi.SENDMESSAGE_ENDPOINT;
import static com.symphony.sfs.ms.chat.generated.api.MessagingApi.SENDSYSTEMMESSAGE_ENDPOINT;
import static com.symphony.sfs.ms.starter.testing.MockMvcUtils.configuredGiven;
import static com.symphony.sfs.ms.starter.testing.MockitoUtils.once;
import static com.symphony.sfs.ms.starter.util.RsaUtils.parseRSAPrivateKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessagingApiTest extends AbstractIntegrationTest {

  protected MessagingApi symphonyMessagingApi;
  private SymphonyMessageSender symphonyMessageSender;
  private SymphonyService symphonyService;
  private MessageStatusService messageStatusService;

  private SessionSupplier<SymphonySession> symphonySession;
  private SessionSupplier<SymphonySession> botSession;
  private MessageDecryptor messageDecryptor;
  private AdminClient mockAdminClient;

  @BeforeEach
  public void setUp(AmazonDynamoDB db, DefaultMockServer mockServer, MessageSource messageSource) throws Exception {
    super.setUp(db, mockServer, messageSource);

    symphonyMessageSender = mock(SymphonyMessageSender.class);
    //streamService = mock(StreamService.class);
    StreamInfo streamInfo = StreamInfo.builder().streamAttributes(StreamAttributes.builder().members(Arrays.asList(1L)).build()).streamType(new StreamType(StreamTypes.ROOM)).build();
    when(streamService.getStreamInfo(anyString(), any(), anyString())).thenReturn(Optional.of(streamInfo));

    PodConfiguration podConfiguration = new PodConfiguration();
    podConfiguration.setUrl("podUrl");
    podConfiguration.setSessionAuth("sessionAuth");
    podConfiguration.setKeyAuth("keyAuth");

    EmpConfig empConfig = new EmpConfig();
    Map<String, Integer> maxTextLengths = new HashMap<>();
    maxTextLengths.put("emp", 1000);
    empConfig.setMaxTextLength(maxTextLengths);

    FederatedAccountRepository federatedAccountRepository = mock(FederatedAccountRepository.class);
    FederatedAccount federatedAccount = FederatedAccount.builder().symphonyUsername("username").symphonyUserId("fromSymphonyUserId").emp("emp").build();
    when(federatedAccountRepository.findBySymphonyId(anyString())).thenReturn(Optional.of(federatedAccount));

    AuthenticationService authenticationService = mock(AuthenticationService.class);
    symphonySession = new SessionSupplier<>(federatedAccount.getSymphonyUsername(), new SymphonyRsaAuthFunction(authenticationService, podConfiguration, parseRSAPrivateKey(chatConfiguration.getSharedPrivateKey().getData())));
    botSession = symphonyAuthFactory.getBotAuth();
    mockAdminClient = spy(MockAdminClient.class);
    EmpClient empClient = mock(EmpClient.class);
    SessionManager sessionManager = new SessionManager(webClient, Collections.emptyList());

    FederatedAccountSessionService federatedAccountSessionService = new FederatedAccountSessionService(federatedAccountRepository);
    DatafeedSessionPool datafeedSessionPool = new DatafeedSessionPool(chatConfiguration, federatedAccountSessionService, symphonyAuthFactory, sessionManager);

    symphonyService = mock(SymphonyService.class);
    messageStatusService = mock(MessageStatusService.class);

    messageDecryptor = mock(MessageDecryptor.class);
    SymphonyMessageService symphonyMessageService = new SymphonyMessageService(empConfig, empClient, tenantDetailRepository, federatedAccountRepository, forwarderQueueConsumer, datafeedSessionPool, symphonyMessageSender, mockAdminClient, null, symphonyService, messageStatusService, podConfiguration, botConfiguration, streamService, new MessageIOMonitor(meterManager), messageSource, messageDecryptor, objectMapper);

    symphonyMessagingApi = new MessagingApi(symphonyMessageService);
  }


  //////////////////////
  //// Send Message ////
  //////////////////////

  @Test
  void sendMessage() {
    SendMessageRequest sendMessageRequest = createTestMessage("streamId", "fromSymphonyUserId", "text", null);
    when(symphonyMessageSender.sendRawMessage("streamId", "fromSymphonyUserId", "<messageML>text</messageML>", null)).thenReturn(Optional.of(new MessageInfoWithCustomEntities().messageId("symphonyMessageId")));
    MessageInfo sendMessageResponse = sendMessage(sendMessageRequest);
    MessageInfo expectedResponse = new MessageInfo().messageId("symphonyMessageId");
    assertEquals(expectedResponse, sendMessageResponse);
  }

  @Test
  void sendFormattedMessage() {
    SendMessageRequest sendMessageRequest = createTestMessage("streamId", "fromSymphonyUserId", "text", FormattingEnum.INFO);
    when(symphonyMessageSender.sendInfoMessage("streamId", "fromSymphonyUserId", "text", null)).thenReturn(Optional.of(new MessageInfoWithCustomEntities().messageId("symphonyMessageId")));
    MessageInfo sendMessageResponse = sendMessage(sendMessageRequest);
    MessageInfo expectedResponse = new MessageInfo().messageId("symphonyMessageId");
    assertEquals(expectedResponse, sendMessageResponse);
  }

  @Test
  void sendMessage_Error() {
    SendMessageRequest sendMessageRequest = createTestMessage("streamId", "wrongSymphonyUserId", "text", null);
    doThrow(new SendMessageFailedProblem()).when(symphonyMessageSender).sendRawMessage("streamId", "wrongSymphonyUserId", "<messageML>text</messageML>", "toSymphonyUserId");
    DefaultProblem response = verifyRequest(sendMessageRequest, HttpStatus.BAD_REQUEST, DefaultProblem.class);
  }

  @Test
  void sendMessage_forwarded() {
    when(mockAdminClient.getBlockedFileTypes(anyString(), anyString())).thenReturn(Optional.of(new BlockedFileTypes()));
    SendMessageRequest sendMessageRequest = createTestMessage("streamId", "fromSymphonyUserId", "text", null);
    sendMessageRequest.setForwarded(true);
    var attachments = Collections.singletonList(new SymphonyAttachment().contentType("image/png").fileName("attachment.png").data("data"));
    sendMessageRequest.setAttachments(attachments);
    when(symphonyMessageSender.sendForwardedMessage("streamId", "fromSymphonyUserId", "text", attachments)).thenReturn(Optional.of(new MessageInfoWithCustomEntities().messageId("symphonyMessageId")));
    MessageInfo sendMessageResponse = sendMessage(sendMessageRequest);
    MessageInfo expectedResponse = new MessageInfo().messageId("symphonyMessageId");
    assertEquals(expectedResponse, sendMessageResponse);

  }

  @Test
  void sendMessageToRoom_noAttachments_noFormatting() {
    SendMessageRequest sendMessageRequest = new SendMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text");
    when(symphonyMessageSender.sendRawMessage("streamId", "fromSymphonyUserId", "<messageML>text</messageML>", null)).thenReturn(Optional.of(new MessageInfoWithCustomEntities().messageId("symphonyMessageId")));
    MessageInfo sendMessageResponse = sendMessage(sendMessageRequest);
    MessageInfo expectedResponse = new MessageInfo().messageId("symphonyMessageId");

    assertEquals(expectedResponse, sendMessageResponse);
  }

  @Test
  void sendMessageToRoom_noAttachments_simpleFormatting() {
    SendMessageRequest sendMessageRequest = new SendMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text").formatting(FormattingEnum.SIMPLE);
    when(symphonyMessageSender.sendSimpleMessage("streamId", "fromSymphonyUserId", "text", null)).thenReturn(Optional.of(new MessageInfoWithCustomEntities().messageId("symphonyMessageId")));
    MessageInfo sendMessageResponse = sendMessage(sendMessageRequest);
    MessageInfo expectedResponse = new MessageInfo().messageId("symphonyMessageId");

    assertEquals(expectedResponse, sendMessageResponse);
  }

  @Test
  void sendMessageToRoom_noAttachments_notificationFormatting() {
    SendMessageRequest sendMessageRequest = new SendMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text").formatting(FormattingEnum.NOTIFICATION);
    when(symphonyMessageSender.sendNotificationMessage("streamId", "fromSymphonyUserId", "text", null)).thenReturn(Optional.of(new MessageInfoWithCustomEntities().messageId("symphonyMessageId")));
    MessageInfo sendMessageResponse = sendMessage(sendMessageRequest);
    MessageInfo expectedResponse = new MessageInfo().messageId("symphonyMessageId");

    assertEquals(expectedResponse, sendMessageResponse);
  }

  @Test
  void sendMessageToRoom_noAttachments_alertFormatting() {
    SendMessageRequest sendMessageRequest = new SendMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text").formatting(FormattingEnum.ALERT);
    when(symphonyMessageSender.sendAlertMessage("streamId", "fromSymphonyUserId", "text", null)).thenReturn(Optional.of(new MessageInfoWithCustomEntities().messageId("symphonyMessageId")));
    MessageInfo sendMessageResponse = sendMessage(sendMessageRequest);
    MessageInfo expectedResponse = new MessageInfo().messageId("symphonyMessageId");

    assertEquals(expectedResponse, sendMessageResponse);
  }

  @Test
  void sendMessageToRoom_noAttachments_infoFormatting() {
    SendMessageRequest sendMessageRequest = new SendMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text").formatting(FormattingEnum.INFO);
    when(symphonyMessageSender.sendInfoMessage("streamId", "fromSymphonyUserId", "text", null)).thenReturn(Optional.of(new MessageInfoWithCustomEntities().messageId("symphonyMessageId")));
    MessageInfo sendMessageResponse = sendMessage(sendMessageRequest);
    MessageInfo expectedResponse = new MessageInfo().messageId("symphonyMessageId");

    assertEquals(expectedResponse, sendMessageResponse);
  }

  @Test
  void sendMessageToRoom_withAttachments() {
    when(mockAdminClient.getBlockedFileTypes(anyString(), anyString())).thenReturn(Optional.of(new BlockedFileTypes()));
    SymphonyAttachment attachment = new SymphonyAttachment().contentType("image/png").data(Base64.encodeBase64String("image".getBytes(StandardCharsets.UTF_8))).fileName("attachment.png");
    SendMessageRequest sendMessageRequest = new SendMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text").addAttachmentsItem(attachment);
    when(symphonyMessageSender.sendRawMessageWithAttachments("streamId", "fromSymphonyUserId", "<messageML>text</messageML>", null, Collections.singletonList(attachment))).thenReturn(Optional.of(new MessageInfoWithCustomEntities().messageId("symphonyMessageId")));
    MessageInfo sendMessageResponse = sendMessage(sendMessageRequest);
    MessageInfo expectedResponse = new MessageInfo().messageId("symphonyMessageId");

    assertEquals(expectedResponse, sendMessageResponse);
  }

  @Test
  void sendMessageToRoom_withAttachments_notBlocked() {
    BlockedFileTypes blockedFileTypes = new BlockedFileTypes();
    blockedFileTypes.add("image/jpg");
    when(mockAdminClient.getBlockedFileTypes(anyString(), anyString())).thenReturn(Optional.of(blockedFileTypes));

    SymphonyAttachment attachment = new SymphonyAttachment().contentType("image/png").data(Base64.encodeBase64String("image".getBytes(StandardCharsets.UTF_8))).fileName("attachment.png");
    SendMessageRequest sendMessageRequest = new SendMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text").tenantId("123").addAttachmentsItem(attachment);
    when(symphonyMessageSender.sendRawMessageWithAttachments("streamId", "fromSymphonyUserId", "<messageML>text</messageML>", null, Collections.singletonList(attachment))).thenReturn(Optional.of(new MessageInfoWithCustomEntities().messageId("symphonyMessageId")));
    MessageInfo sendMessageResponse = sendMessage(sendMessageRequest);
    MessageInfo expectedResponse = new MessageInfo().messageId("symphonyMessageId");

    assertEquals(expectedResponse, sendMessageResponse);
  }

  @Test
  void sendMessageToRoom_withAttachments_blocked() {
    BlockedFileTypes blockedFileTypes = new BlockedFileTypes();
    blockedFileTypes.addAll(List.of("image/png", "application/pdf"));
    when(mockAdminClient.getBlockedFileTypes(anyString(), anyString())).thenReturn(Optional.of(blockedFileTypes));

    SymphonyAttachment attachment = new SymphonyAttachment().contentType("image/png").data(Base64.encodeBase64String("image".getBytes(StandardCharsets.UTF_8))).fileName("attachment.png");
    SendMessageRequest sendMessageRequest = new SendMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text").tenantId("123").addAttachmentsItem(attachment);
    when(symphonyMessageSender.sendRawMessageWithAttachments("streamId", "fromSymphonyUserId", "<messageML>text</messageML>", null, Collections.singletonList(attachment))).thenReturn(Optional.of(new MessageInfoWithCustomEntities().messageId("symphonyMessageId")));
    DefaultProblem blockedProblem =  verifyRequest(sendMessageRequest, HttpStatus.CONFLICT, DefaultProblem.class);


    assertNotNull(blockedProblem.getParameters().get("attachmentsBlocked"));


    List<Map<String, String>> expected = Collections.singletonList(Map.of("mimeType", "image/png", "name", "attachment.png"));
    assertEquals(expected, blockedProblem.getParameters().get("attachmentsBlocked"));

  }

  @Test
  void sendMessage_withJsonData_notRecognized1() {
    SendMessageRequest sendMessageRequest = createTestMessage("streamId", "fromSymphonyUserId", "text", null).jsonData("{ \"entry\": \"value\"}").presentationML("<div data-format=\"PresentationML\" data-version=\"2.0\">PresentationML Content</div>");
    when(symphonyMessageSender.sendRawMessage("streamId", "fromSymphonyUserId", "<messageML>text</messageML>", null)).thenReturn(Optional.of(new MessageInfoWithCustomEntities().messageId("symphonyMessageId")));
    MessageInfo sendMessageResponse = sendMessage(sendMessageRequest);
    MessageInfo expectedResponse = new MessageInfo().messageId("symphonyMessageId");
    assertEquals(expectedResponse, sendMessageResponse);
  }

  @Test
  void sendMessage_withJsonData_notRecognized2() {
    SendMessageRequest sendMessageRequest = createTestMessage("streamId", "fromSymphonyUserId", "text", null).jsonData("{ \"type\": \"unknownType\"}").presentationML("<div data-format=\"PresentationML\" data-version=\"2.0\">PresentationML Content</div>");
    when(symphonyMessageSender.sendRawMessage("streamId", "fromSymphonyUserId", "<messageML>text</messageML>", null)).thenReturn(Optional.of(new MessageInfoWithCustomEntities().messageId("symphonyMessageId")));
    MessageInfo sendMessageResponse = sendMessage(sendMessageRequest);
    MessageInfo expectedResponse = new MessageInfo().messageId("symphonyMessageId");
    assertEquals(expectedResponse, sendMessageResponse);
  }

  @Test
  void sendMessage_withJsonData_sendContact() {
    SendMessageRequest sendMessageRequest = createTestMessage("streamId", "fromSymphonyUserId", "text", null).jsonData("{ \"type\": \"send_contacts\"}").presentationML("<div data-format=\"PresentationML\" data-version=\"2.0\">PresentationML Content</div>");
    when(symphonyMessageSender.sendContactMessage("streamId", "fromSymphonyUserId", "text", "{ \"type\": \"send_contacts\"}", "<div data-format=\"PresentationML\" data-version=\"2.0\">PresentationML Content</div>")).thenReturn(Optional.of(new MessageInfoWithCustomEntities().messageId("symphonyMessageId")));
    MessageInfo sendMessageResponse = sendMessage(sendMessageRequest);
    MessageInfo expectedResponse = new MessageInfo().messageId("symphonyMessageId");
    assertEquals(expectedResponse, sendMessageResponse);
  }

  /////////////////////////////
  //// Send System Message ////
  /////////////////////////////


  @Test
  void sendSystemMessageToRoom_noFormatting() {
    SendSystemMessageRequest sendsystemMessageRequest = new SendSystemMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text");
    when(symphonyMessageSender.sendRawMessage(botSession, "streamId", "<messageML>text</messageML>")).thenReturn(Optional.of(new MessageInfoWithCustomEntities().messageId("symphonyMessageId")));
    MessageInfo sendSystemMessageResponse = sendSystemMessage(sendsystemMessageRequest);
    MessageInfo expectedResponse = new MessageInfo().messageId("symphonyMessageId");

    assertEquals(expectedResponse, sendSystemMessageResponse);
  }

  @Test
  void sendSystemMessageToRoom_simpleFormatting() {
    SendSystemMessageRequest sendsystemMessageRequest = new SendSystemMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text").formatting(FormattingEnum.SIMPLE);
    when(symphonyMessageSender.sendSimpleMessage(botSession, "streamId", "text")).thenReturn(Optional.of(new MessageInfoWithCustomEntities().messageId("symphonyMessageId")));
    MessageInfo sendSystemMessageResponse = sendSystemMessage(sendsystemMessageRequest);
    MessageInfo expectedResponse = new MessageInfo().messageId("symphonyMessageId");

    assertEquals(expectedResponse, sendSystemMessageResponse);
  }

  @Test
  void sendSystemMessageToRoom_notificationFormatting() {
    SendSystemMessageRequest sendsystemMessageRequest = new SendSystemMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text").formatting(FormattingEnum.NOTIFICATION);
    when(symphonyMessageSender.sendNotificationMessage(botSession, "streamId", "text")).thenReturn(Optional.of(new MessageInfoWithCustomEntities().messageId("symphonyMessageId")));
    MessageInfo sendSystemMessageResponse = sendSystemMessage(sendsystemMessageRequest);
    MessageInfo expectedResponse = new MessageInfo().messageId("symphonyMessageId");

    assertEquals(expectedResponse, sendSystemMessageResponse);
  }

  @Test
  void sendSystemMessageToRoom_alertFormatting() {
    SendSystemMessageRequest sendsystemMessageRequest = new SendSystemMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text").title("title").formatting(FormattingEnum.ALERT);
    when(symphonyMessageSender.sendAlertMessage(botSession, "streamId", "text", "title", Collections.emptyList())).thenReturn(Optional.of(new MessageInfoWithCustomEntities().messageId("symphonyMessageId")));
    MessageInfo sendSystemMessageResponse = sendSystemMessage(sendsystemMessageRequest);
    MessageInfo expectedResponse = new MessageInfo().messageId("symphonyMessageId");

    assertEquals(expectedResponse, sendSystemMessageResponse);
  }

  @Test
  void sendSystemMessageToRoom_infoFormatting() {
    SendSystemMessageRequest sendsystemMessageRequest = new SendSystemMessageRequest().streamId("streamId").fromSymphonyUserId("fromSymphonyUserId").text("text").formatting(FormattingEnum.INFO);
    when(symphonyMessageSender.sendInfoMessage(botSession, "streamId", "text")).thenReturn(Optional.of(new MessageInfoWithCustomEntities().messageId("symphonyMessageId")));
    MessageInfo sendSystemMessageResponse = sendSystemMessage(sendsystemMessageRequest);
    MessageInfo expectedResponse = new MessageInfo().messageId("symphonyMessageId");

    assertEquals(expectedResponse, sendSystemMessageResponse);
  }

  ///////////////////////////////
  //// Mark Messages As Read ////
  ///////////////////////////////

  @Test
  void markMessagesAsRead() {
    Long timestamp = Instant.now().toEpochMilli();
    List<String> messagesIds = Collections.singletonList("messageId");
    SetMessagesAsReadRequest setMessagesAsReadRequest = new SetMessagesAsReadRequest().symphonyUserId("symphonyUserId").streamId("streamId").messageIds(messagesIds).timestamp(timestamp);
    SendMessageStatusRequest sendMessageStatusRequest = new SendMessageStatusRequest(timestamp, messagesIds, true, "streamId");
    markMessagesAsRead(setMessagesAsReadRequest);

    verify(messageStatusService, once()).markMessagesAsRead(eq("podUrl"), ArgumentMatchers.<SessionSupplier<SymphonySession>>any(), eq(Collections.singletonList(sendMessageStatusRequest)));
  }

  ///////////////////////////
  //// Retrieve Messages ////
  ///////////////////////////

  @Test
  void retrieveMessages_ok() {
    OffsetDateTime start = OffsetDateTime.now().minusHours(1);
    OffsetDateTime end = OffsetDateTime.now();
    RetrieveMessagesRequest retrieveMessagesRequest = new RetrieveMessagesRequest().messagesIds(Arrays.asList(new MessageId().messageId("7ThaU2OJ3nJ8A9Kz0qjheX___oOLK1YmbQ"), new MessageId().messageId("tAMLft-K2vyqWXnHFMeCh3___oQbTZrDdA"))).symphonyUserId("fromSymphonyUserId")
      .threadId("streamId").startTime(start).endTime(end);

    SBEEventMessage sbeEventMessage = SBEEventMessage.builder().messageId("7ThaU2OJ3nJ8A9Kz0qjheX///oOLK1YmbQ==").disclaimer("disclaimer1").text("This is the message 1")
      .ingestionDate(123L)
      .from(EventUser.builder()
        .firstName("first")
        .surName("last")
        .id(12345L)
        .company("company")
        .build()).build();



    SBEEventMessage sbeEventMessage2 = SBEEventMessage.builder().messageId("tAMLft+K2vyqWXnHFMeCh3///oQbTZrDdA==").disclaimer("disclaimer2").text("This is the message 2")
      .ingestionDate(123L)
      .from(EventUser.builder()
        .firstName("first")
        .surName("last")
        .id(12345L)
        .company("company")
        .build()).build();

    ThreadMessagesResponse threadMessagesResponse = new ThreadMessagesResponse();
    threadMessagesResponse.setEnvelopes(List.of(MessageEnvelope.builder().message(sbeEventMessage).build(), MessageEnvelope.builder().message(sbeEventMessage2).build()));

    when(streamService.retrieveSocialMessagesList(anyString(), any(), anyString(), anyInt(), anyLong(), anyLong())).thenReturn(Optional.of(threadMessagesResponse));

    MessageInfoWithCustomEntities messageInfo1 = new MessageInfoWithCustomEntities()
      .messageId("7ThaU2OJ3nJ8A9Kz0qjheX___oOLK1YmbQ")
      .disclaimer("disclaimer1")
      .message("This is the message 1")
      .firstName("first")
      .lastName("last")
      .symphonyId("12345")
      .timestamp(123L);
    MessageInfoWithCustomEntities messageInfo2 = new MessageInfoWithCustomEntities()
      .messageId("tAMLft-K2vyqWXnHFMeCh3___oQbTZrDdA")
      .disclaimer("disclaimer2")
      .message("This is the message 2")
      .firstName("first")
      .lastName("last")
      .symphonyId("12345")
      .timestamp(123L);

    try {
      when(symphonyMessageSender.decryptAndBuildMessageInfo(eq(sbeEventMessage), eq("fromSymphonyUserId"), any(), any())).thenReturn(messageInfo1);
      when(symphonyMessageSender.decryptAndBuildMessageInfo(eq(sbeEventMessage2), eq("fromSymphonyUserId"), any(), any())).thenReturn(messageInfo2);
    } catch (Exception e) {
      fail();
    }

    RetrieveMessagesResponse response = retrieveMessages(retrieveMessagesRequest);
    RetrieveMessagesResponse expectedResponse = new RetrieveMessagesResponse().messages(Arrays.asList(
      new MessageInfo()
        .messageId("7ThaU2OJ3nJ8A9Kz0qjheX___oOLK1YmbQ")
        .disclaimer("disclaimer1")
        .message("This is the message 1")
        .firstName("first")
        .lastName("last")
        .symphonyId("12345")
        .timestamp(123L),
      new MessageInfo()
        .messageId("tAMLft-K2vyqWXnHFMeCh3___oQbTZrDdA")
        .disclaimer("disclaimer2")
        .message("This is the message 2")
        .firstName("first")
        .lastName("last")
        .symphonyId("12345")
        .timestamp(123L)
    ));

    assertEquals(expectedResponse, response);
  }

  @Test
  void retrieveMessages_ok_withParent() {

    OffsetDateTime start = OffsetDateTime.now().minusHours(1);
    OffsetDateTime end = OffsetDateTime.now();
    RetrieveMessagesRequest retrieveMessagesRequest = new RetrieveMessagesRequest().messagesIds(Collections.singletonList(new MessageId().messageId("messageIdw"))).symphonyUserId("fromSymphonyUserId")
      .threadId("streamId").startTime(start).endTime(end);

    SBEEventMessage sbeEventMessage = SBEEventMessage.builder().messageId("messageId1").disclaimer("disclaimer1").text("12345This is the message 1")
      .ingestionDate(123L)
      .parsedCustomEntities(Collections.singletonList(
        CustomEntity.builder()
          .type(CustomEntity.QUOTE_TYPE)
          .endIndex(5)
          .data(Map.of("id", "otherMsg"))
          .build()
      ))
      .from(EventUser.builder()
        .firstName("first")
        .surName("last")
        .id(12345L)
        .company("company")
        .build()).build();


    SBEEventMessage sbeEventMessage2 = SBEEventMessage.builder().messageId("otherMsg").disclaimer("disclaimer2").text("123456This is the message 2")
      .ingestionDate(123L)
      .parsedCustomEntities(Collections.singletonList(
        CustomEntity.builder()
          .type(CustomEntity.QUOTE_TYPE)
          .endIndex(6)
          .data(Map.of("id", "messageId2"))
          .build()
      ))
      .from(EventUser.builder()
        .firstName("first")
        .surName("last")
        .id(12345L)
        .company("company")
        .build())
      .attachments(Collections.singletonList(
        MessageAttachment.builder().contentType("image/png").name("hello.png").build()
      )).build();

    ThreadMessagesResponse threadMessagesResponse = new ThreadMessagesResponse();
    threadMessagesResponse.setEnvelopes(List.of(MessageEnvelope.builder().message(sbeEventMessage).build(), MessageEnvelope.builder().message(sbeEventMessage2).build()));

    when(streamService.retrieveSocialMessagesList(anyString(), any(), anyString(), anyInt(), anyLong(), anyLong())).thenReturn(Optional.of(threadMessagesResponse));

    MessageInfoWithCustomEntities messageInfo = new MessageInfoWithCustomEntities()
      .messageId("messageIdw")
      .disclaimer("disclaimer1")
      .message("This is the message 1")
      .firstName("first")
      .lastName("last")
      .symphonyId("12345")
      .timestamp(123L)
      .parentMessage(new MessageInfo()
        .messageId("otherMsg")
        .disclaimer("disclaimer2")
        .message("This is the message 2")
        .firstName("first")
        .lastName("last")
        .symphonyId("12345")
        .timestamp(123L)
        .addAttachmentsItem(new AttachmentInfo().contentType("image/png").fileName("hello.png")));

    try {
      when(symphonyMessageSender.decryptAndBuildMessageInfo(eq(sbeEventMessage), eq("fromSymphonyUserId"), any(), any())).thenReturn(messageInfo);
    } catch (Exception e) {
      fail();
    }

    RetrieveMessagesResponse response = retrieveMessages(retrieveMessagesRequest);
    RetrieveMessagesResponse expectedResponse = new RetrieveMessagesResponse().messages(Arrays.asList(
      new MessageInfo()
        .messageId("messageIdw")
        .disclaimer("disclaimer1")
        .message("This is the message 1")
        .firstName("first")
        .lastName("last")
        .symphonyId("12345")
        .timestamp(123L)
        .parentMessage(new MessageInfo()
          .messageId("otherMsg")
          .disclaimer("disclaimer2")
          .message("This is the message 2")
          .firstName("first")
          .lastName("last")
          .symphonyId("12345")
          .timestamp(123L)
          .addAttachmentsItem(new AttachmentInfo().contentType("image/png").fileName("hello.png")))
    ));

    assertEquals(expectedResponse, response);
  }

  @Test
  void retrieveMessages_ok_specialCharacters() {
    OffsetDateTime start = OffsetDateTime.now().minusHours(1);
    OffsetDateTime end = OffsetDateTime.now();

    RetrieveMessagesRequest retrieveMessagesRequest = new RetrieveMessagesRequest().messagesIds(Collections.singletonList(new MessageId().messageId("messageIdw"))).symphonyUserId("fromSymphonyUserId")
      .threadId("streamId").startTime(start).endTime(end);
    CustomEntity ce = CustomEntity.builder().type(CustomEntity.QUOTE_TYPE).endIndex(0).data(Collections.singletonMap("id", "messageId2")).build();
    SBEEventMessage sbeEventMessage = SBEEventMessage.builder().messageId("messageId1").disclaimer("disclaimer1").text("This \\+ is \\_the\\_message \\*1")
      .ingestionDate(123L)
      .parsedCustomEntities(Collections.singletonList(ce))
      .from(EventUser.builder()
        .firstName("first")
        .surName("last")
        .id(12345L)
        .company("company")
        .build()).build();
    SBEEventMessage sbeEventMessage2 = SBEEventMessage.builder().messageId("messageId11").disclaimer("disclaimer1").text("This \\+ is \\_the\\_inlinemessage \\*1")
        .ingestionDate(123L)
        .from(EventUser.builder()
          .firstName("first")
          .surName("last")
          .id(12345L)
          .company("company")
          .build()).build();

    ThreadMessagesResponse threadMessagesResponse = new ThreadMessagesResponse();
    threadMessagesResponse.setEnvelopes(List.of(MessageEnvelope.builder().message(sbeEventMessage).build(), MessageEnvelope.builder().message(sbeEventMessage2).build()));

    when(streamService.retrieveSocialMessagesList(anyString(), any(), anyString(), anyInt(), anyLong(), anyLong())).thenReturn(Optional.of(threadMessagesResponse));

    MessageInfoWithCustomEntities messageInfo = new MessageInfoWithCustomEntities()
      .messageId("messageIdw")
      .disclaimer("disclaimer1")
      .message("This \\+ is \\_the\\_message \\*1")
      .firstName("first")
      .lastName("last")
      .symphonyId("12345")
      .timestamp(123L)
      .parentMessage(new MessageInfoWithCustomEntities()
        .messageId("messageIdw")
        .disclaimer("disclaimer1")
        .message("This \\+ is \\_the\\_inlinemessage \\*1")
        .firstName("first")
        .lastName("last")
        .symphonyId("12345")
        .timestamp(123L));

    try {
      when(symphonyMessageSender.decryptAndBuildMessageInfo(eq(sbeEventMessage), eq("fromSymphonyUserId"), any(), any())).thenReturn(messageInfo);
    } catch (Exception e) {
      fail();
    }

    RetrieveMessagesResponse response = retrieveMessages(retrieveMessagesRequest);
    MessageInfo msg = new MessageInfo()
      .messageId("messageIdw")
      .disclaimer("disclaimer1")
      .message("This \\+ is \\_the\\_message \\*1")
      .firstName("first")
      .lastName("last")
      .symphonyId("12345")
      .timestamp(123L);

    MessageInfo parentMsg = new MessageInfo()
      .messageId("messageIdw")
      .disclaimer("disclaimer1")
      .message("This \\+ is \\_the\\_inlinemessage \\*1")
      .firstName("first")
      .lastName("last")
      .symphonyId("12345")
      .timestamp(123L);
    msg.setParentMessage(parentMsg);
    RetrieveMessagesResponse expectedResponse = new RetrieveMessagesResponse().messages(Collections.singletonList(msg));

    assertEquals(expectedResponse, response);
  }

  @Test
  void retrieveMessages_fails() {
    OffsetDateTime start = OffsetDateTime.now().minusHours(1);
    OffsetDateTime end = OffsetDateTime.now();

    RetrieveMessagesRequest retrieveMessagesRequest = new RetrieveMessagesRequest().messagesIds(Arrays.asList(new MessageId().messageId("messageId1"), new MessageId().messageId("messageId2"), new MessageId().messageId("messageId3"))).symphonyUserId("fromSymphonyUserId")
      .threadId("streamId").startTime(start).endTime(end);


    SBEEventMessage sbeEventMessage = SBEEventMessage.builder().messageId("messageId1").disclaimer("disclaimer1").text("This \\+ is \\_the\\_message \\*1")
      .ingestionDate(123L)
      .from(EventUser.builder()
        .firstName("first")
        .surName("last")
        .id(12345L)
        .company("company")
        .build()).build();

    ThreadMessagesResponse threadMessagesResponse = new ThreadMessagesResponse();
    threadMessagesResponse.setEnvelopes(List.of(MessageEnvelope.builder().message(sbeEventMessage).build()));

    when(streamService.retrieveSocialMessagesList(anyString(), any(), anyString(), anyInt(), anyLong(), anyLong())).thenReturn(Optional.of(threadMessagesResponse));


    HttpRequestUtils.getRequestFail(symphonyMessagingApi, retrieveMessagesRequest, RETRIEVEMESSAGES_ENDPOINT, Collections.emptyList(), objectMapper, tracer, RetrieveMessageFailedProblem.class.getName(), null, HttpStatus.BAD_REQUEST);
  }

  private <RESPONSE> RESPONSE verifyRequest(SendMessageRequest sendMessageRequest, HttpStatus expectedStatus, Class<RESPONSE> response) {
    return configuredGiven(objectMapper, new ExceptionHandling(tracer), symphonyMessagingApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(sendMessageRequest)
      .when()
      .post(SENDMESSAGE_ENDPOINT)
      .then()
      .statusCode(expectedStatus.value())
      .extract().response().body()
      .as(response);
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

  private MessageInfo sendMessage(SendMessageRequest sendMessageRequest) {
    return configuredGiven(objectMapper, new ExceptionHandling(tracer), symphonyMessagingApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(sendMessageRequest)
      .when()
      .post(SENDMESSAGE_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value())
      .extract().response().body()
      .as(MessageInfo.class);
  }

  private MessageInfo sendSystemMessage(SendSystemMessageRequest sendSystemMessageRequest) {
    return configuredGiven(objectMapper, new ExceptionHandling(tracer), symphonyMessagingApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(sendSystemMessageRequest)
      .when()
      .post(SENDSYSTEMMESSAGE_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value())
      .extract().response().body()
      .as(MessageInfo.class);
  }

  private void markMessagesAsRead(SetMessagesAsReadRequest setMessagesAsReadRequest) {
    configuredGiven(objectMapper, new ExceptionHandling(tracer), symphonyMessagingApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(setMessagesAsReadRequest)
      .when()
      .post(MARKMESSAGESASREAD_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value());
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
