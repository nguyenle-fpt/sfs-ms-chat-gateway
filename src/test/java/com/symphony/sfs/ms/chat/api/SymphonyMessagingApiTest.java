package com.symphony.sfs.ms.chat.api;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.symphony.sfs.ms.chat.api.util.AbstractIntegrationTest;
import com.symphony.sfs.ms.chat.generated.model.SendMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.SendMessageRequest;
import com.symphony.sfs.ms.chat.generated.model.SendMessageResponse;
import com.symphony.sfs.ms.chat.service.SymphonyMessageService;
import com.symphony.sfs.ms.starter.config.ExceptionHandling;
import io.fabric8.mockwebserver.DefaultMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.zalando.problem.DefaultProblem;

import static com.symphony.sfs.ms.chat.generated.api.SymphonyMessagingApi.SENDMESSAGE_ENDPOINT;
import static com.symphony.sfs.ms.starter.testing.MockMvcUtils.configuredGiven;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class SymphonyMessagingApiTest extends AbstractIntegrationTest {

  private SymphonyMessageService symphonyMessageService;

  protected SymphonyMessagingApi symphonyMessagingApi;

  @BeforeEach
  public void setUp(AmazonDynamoDB db, DefaultMockServer mockServer) throws Exception {
    super.setUp(db, mockServer);

    symphonyMessageService = mock(SymphonyMessageService.class);
    symphonyMessagingApi = new SymphonyMessagingApi(symphonyMessageService);
  }

  @Test
  void sendMessage() {

    SendMessageRequest sendMessageRequest = new SendMessageRequest();
    sendMessageRequest.setStreamId("streamId");
    sendMessageRequest.setFromSymphonyUserId("fromSymphonyUserId");
    sendMessageRequest.setText("text");

    SendMessageResponse response = configuredGiven(objectMapper, new ExceptionHandling(), symphonyMessagingApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(sendMessageRequest)
      .when()
      .post(SENDMESSAGE_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value())
      .extract().response().body()
      .as(SendMessageResponse.class);

  }

  @Test
  void sendMessage_Error() {

    SendMessageRequest sendMessageRequest = new SendMessageRequest();
    sendMessageRequest.setStreamId("streamId");
    sendMessageRequest.setFromSymphonyUserId("wrongSymphonyUserId");
    sendMessageRequest.setText("text");

    doThrow(new SendMessageFailedProblem()).when(symphonyMessageService).sendRawMessage("streamId", "wrongSymphonyUserId", "text");

    configuredGiven(objectMapper, new ExceptionHandling(), symphonyMessagingApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(sendMessageRequest)
      .when()
      .post(SENDMESSAGE_ENDPOINT)
      .then()
      .statusCode(HttpStatus.BAD_REQUEST.value())
      .extract().response().body()
      .as(DefaultProblem.class);

  }
}
