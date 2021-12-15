package com.symphony.sfs.ms.chat.service.symphony;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.symphony.sfs.ms.chat.datafeed.SBEEventMessage;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.security.ISessionManager;
import com.symphony.sfs.ms.starter.security.SessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;

import java.util.Collections;
import java.util.Optional;

import static com.symphony.sfs.ms.starter.logging.WebRequestLoggingFilter.BASE_PATH;
import static com.symphony.sfs.ms.starter.logging.WebRequestLoggingFilter.BASE_URI;
import static com.symphony.sfs.ms.starter.util.WebClientUtils.logWebClientError;

@Service
@Slf4j
@RequiredArgsConstructor
public class SymphonyService {

  private final ISessionManager sessionManager;
  private final PodConfiguration podConfiguration;

  public static final String GETMESSAGEENCRYPTED = "/webcontroller/dataquery/retrieveMessagePayload?{messageId}";
  public static final String GETATTACHMENT = "/agent/v1/stream/{streamId}/attachment?messageId={messageId}&fileId={fileId}";
  public static final String REPLYTOMESSAGE = "/webcontroller/ingestor/MessageService/reply?replyMessage=true";



  @NewSpan
  public Optional<SBEEventMessage> getEncryptedMessage(String messageId, SessionSupplier<SymphonySession> session) {
    try {
      SBEEventMessage response = sessionManager.getWebClient(session).get()
      .uri(podConfiguration.getUrl() + GETMESSAGEENCRYPTED, messageId)
      .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .attribute(BASE_URI, podConfiguration.getUrl())
      .attribute(BASE_PATH, GETMESSAGEENCRYPTED)
      .retrieve()
      .bodyToMono(SBEEventMessage.class)
      .block();

    return Optional.of(response);
  } catch (Exception e) {
    logWebClientError(LOG, GETMESSAGEENCRYPTED.replace("{messageId}", messageId), e);
    return Optional.empty();
  }

  }
  /**
   * Retrieve an attachment of a message
   *
   * @param streamId  Conversation ID of the IM, MIM, or chatroom where the message containing the attachment is located
   * @param messageId Message ID of the message containing the attachment
   * @param fileId    ID of the attachment to download
   * @return The attachment body encoded in Base64
   */
  public String getAttachment(String streamId, String messageId, String fileId, SessionSupplier<SymphonySession> session) {
    String response = sessionManager.getWebClient(session).get()
      .uri(podConfiguration.getUrl() + GETATTACHMENT, streamId, messageId, fileId)
      .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .attribute(BASE_URI, podConfiguration.getUrl())
      .attribute(BASE_PATH, GETATTACHMENT)
      .retrieve()
      .bodyToMono(String.class)
      .block();

    return response;
  }

  public SBEEventMessage sendReplyMessage(SBEEventMessage message, SessionSupplier<SymphonySession> session) throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

    String payload = objectMapper.writeValueAsString(Collections.singletonList(message));
    Object response = sessionManager.getWebClient(session).post()
      .uri(podConfiguration.getUrl() + REPLYTOMESSAGE)
      .contentType(MediaType.APPLICATION_JSON)
      .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.APPLICATION_JSON_VALUE)
      .body(BodyInserters.fromFormData("messages", payload))
      .attribute(BASE_URI, podConfiguration.getUrl())
      .attribute(BASE_PATH, REPLYTOMESSAGE)
      .retrieve().bodyToMono(Object.class)
      .block();

    JsonNode jsonNode = objectMapper.valueToTree(response);
    String firstNode = jsonNode.fieldNames().next();
    JsonNode messageNode = jsonNode.get(firstNode).get("message");
    return objectMapper.treeToValue(messageNode, SBEEventMessage.class);
  }

}
