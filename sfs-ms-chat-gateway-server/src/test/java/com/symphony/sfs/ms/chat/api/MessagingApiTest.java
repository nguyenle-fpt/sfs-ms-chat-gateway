package com.symphony.sfs.ms.chat.api;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.symphony.sfs.ms.chat.api.util.AbstractIntegrationTest;
import com.symphony.sfs.ms.chat.generated.model.FormattingEnum;
import com.symphony.sfs.ms.chat.generated.model.SendMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.SendMessageRequest;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.MessageIOMonitor;
import com.symphony.sfs.ms.chat.service.SymphonyMessageSender;
import com.symphony.sfs.ms.chat.service.SymphonyMessageService;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.config.properties.common.PemResource;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.stream.StreamAttributes;
import com.symphony.sfs.ms.starter.symphony.stream.StreamInfo;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.symphony.user.UsersInfoService;
import io.fabric8.mockwebserver.DefaultMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.zalando.problem.DefaultProblem;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessagingApiTest extends AbstractIntegrationTest {

  protected MessagingApi symphonyMessagingApi;
  private SymphonyMessageSender symphonyMessageSender;

  @BeforeEach
  public void setUp(AmazonDynamoDB db, DefaultMockServer mockServer) throws Exception {
    super.setUp(db, mockServer);

    symphonyMessageSender = mock(SymphonyMessageSender.class);
    StreamService streamService = mock(StreamService.class);

    BotConfiguration botConfiguration = new BotConfiguration();
    botConfiguration.setUsername("username");
    botConfiguration.setEmailAddress("emailAddress");
    botConfiguration.setPrivateKey(new PemResource("-----botConfigurationPrivateKey"));

    PodConfiguration podConfiguration = new PodConfiguration();
    podConfiguration.setUrl("podUrl");
    podConfiguration.setSessionAuth("sessionAuth");
    podConfiguration.setKeyAuth("keyAuth");

    FederatedAccountRepository federatedAccountRepository = mock(FederatedAccountRepository.class);
    AuthenticationService authenticationService = mock(AuthenticationService.class);
    AdminClient adminClient = mock(AdminClient.class);
    EmpClient empClient = mock(EmpClient.class);
    UsersInfoService usersInfoService = mock(UsersInfoService.class);

    SymphonyMessageService symphonyMessageService = new SymphonyMessageService(empClient, federatedAccountRepository, forwarderQueueConsumer, datafeedSessionPool, symphonyMessageSender, adminClient, null, null, podConfiguration, botConfiguration, authenticationService, usersInfoService, streamService, new MessageIOMonitor(meterManager), channelService);
    symphonyMessagingApi = new MessagingApi(symphonyMessageService);

    FederatedAccount federatedAccount = FederatedAccount.builder().symphonyUserId("fromSymphonyUserId").build();
    when(federatedAccountRepository.findBySymphonyId(anyString())).thenReturn(Optional.of(federatedAccount));

    StreamInfo streamInfo = StreamInfo.builder().streamAttributes(StreamAttributes.builder().members(Arrays.asList(1L)).build()).build();
    when(streamService.getStreamInfo(anyString(), any(), anyString())).thenReturn(Optional.of(streamInfo));
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

  private SendMessageRequest createTestMessage(String streamId, String fromSymphonyUserId, String text, FormattingEnum formatting) {
    SendMessageRequest sendMessageRequest = new SendMessageRequest();
    sendMessageRequest.setStreamId(streamId);
    sendMessageRequest.setFromSymphonyUserId(fromSymphonyUserId);
    sendMessageRequest.setFormatting(formatting);
    sendMessageRequest.setText(text);
    return sendMessageRequest;
  }
}
