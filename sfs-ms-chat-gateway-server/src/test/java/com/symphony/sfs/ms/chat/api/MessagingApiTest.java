package com.symphony.sfs.ms.chat.api;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.symphony.sfs.ms.chat.api.util.AbstractIntegrationTest;
import com.symphony.sfs.ms.chat.generated.model.SendMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.SendMessageRequest;
import com.symphony.sfs.ms.chat.generated.model.SendMessageRequest.FormattingEnum;
import com.symphony.sfs.ms.chat.generated.model.SendMessageResponse;
import com.symphony.sfs.ms.chat.service.SymphonyMessageService;
import com.symphony.sfs.ms.starter.config.ExceptionHandling;
import io.fabric8.mockwebserver.DefaultMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.zalando.problem.DefaultProblem;

import static com.symphony.sfs.ms.chat.generated.api.MessagingApi.SENDMESSAGE_ENDPOINT;
import static com.symphony.sfs.ms.starter.testing.MockMvcUtils.configuredGiven;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class MessagingApiTest extends AbstractIntegrationTest {

  private SymphonyMessageService symphonyMessageService;

  protected MessagingApi symphonyMessagingApi;

  @BeforeEach
  public void setUp(AmazonDynamoDB db, DefaultMockServer mockServer) throws Exception {
    super.setUp(db, mockServer);

    symphonyMessageService = mock(SymphonyMessageService.class);
    symphonyMessagingApi = new MessagingApi(symphonyMessageService);
  }

  @Test
  void sendMessage() {
    SendMessageRequest sendMessageRequest = this.createTestMessage("streamId","fromSymphonyUserId", "text", null);
    SendMessageResponse response = this.verifyRequest(sendMessageRequest, HttpStatus.OK, SendMessageResponse.class);
  }

  @Test
  void sendFormattedMessage() {
    SendMessageRequest sendMessageRequest = this.createTestMessage("streamId", "fromSymphonyUserId", "text", FormattingEnum.INFO);
    SendMessageResponse response = this.verifyRequest(sendMessageRequest, HttpStatus.OK, SendMessageResponse.class);
  }

  @Test
  void sendMessage_Error() {
    SendMessageRequest sendMessageRequest = this.createTestMessage("streamId", "wrongSymphonyUserId", "text", null);
    doThrow(new SendMessageFailedProblem()).when(symphonyMessageService).sendRawMessage("streamId", "wrongSymphonyUserId", "<messageML>text</messageML>");
    DefaultProblem response = this.verifyRequest(sendMessageRequest, HttpStatus.BAD_REQUEST, DefaultProblem.class);
  }

  private <RESPONSE> RESPONSE verifyRequest(SendMessageRequest sendMessageRequest, HttpStatus expectedStatus, Class<RESPONSE> response) {
    return configuredGiven(objectMapper, new ExceptionHandling(), symphonyMessagingApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(sendMessageRequest)
      .when()
      .post(SENDMESSAGE_ENDPOINT)
      .then()
      .statusCode(expectedStatus.value())
      .extract().response().body()
      .as(response);
  }

  private SendMessageRequest createTestMessage(String streamId, String fromSymphonyUserId, String text, FormattingEnum formatting){
    SendMessageRequest sendMessageRequest = new SendMessageRequest();
    sendMessageRequest.setStreamId(streamId);
    sendMessageRequest.setFromSymphonyUserId(fromSymphonyUserId);
    sendMessageRequest.setFormatting(formatting);
    sendMessageRequest.setText(text);
    return sendMessageRequest;
  }
}
