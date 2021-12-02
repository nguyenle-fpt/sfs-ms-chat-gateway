package com.symphony.sfs.ms.chat.service.symphony;

import clients.symphony.api.constants.PodConstants;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.symphony.sfs.ms.chat.datafeed.SBEEventMessage;
import com.symphony.sfs.ms.chat.generated.model.MessageInfo;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.SymMessageParser;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyInboundMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.Optional;

import static com.symphony.sfs.ms.starter.logging.WebRequestLoggingFilter.BASE_PATH;
import static com.symphony.sfs.ms.starter.logging.WebRequestLoggingFilter.BASE_URI;
import static com.symphony.sfs.ms.starter.util.WebClientUtils.logWebClientError;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Service
@Slf4j
@RequiredArgsConstructor
public class SymphonyService {

  private final WebClient webClient;
  private final PodConfiguration podConfiguration;
  private final SymMessageParser symMessageParser;

  public static final String GETMESSAGE = "/agent/v1/message/{messageId}";
  public static final String GETMESSAGEENCRYPTED = "/webcontroller/dataquery/retrieveMessagePayload?{messageId}";
  public static final String GETATTACHMENT = "/agent/v1/stream/{streamId}/attachment?messageId={messageId}&fileId={fileId}";
  public static final String REPLYTOMESSAGE = "/webcontroller/ingestor/MessageService/reply?replyMessage=true";

  @NewSpan
  public void removeMemberFromRoom(String streamId, SymphonySession session) {
    webClient.post()
      .uri(podConfiguration.getUrl() + PodConstants.REMOVEMEMBER, streamId)
      .header(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_VALUE)
      .header("sessionToken", session.getSessionToken())
      .header("keyManagerToken", session.getKmToken())
      .attribute(BASE_URI, podConfiguration.getUrl())
      .attribute(BASE_PATH, PodConstants.REMOVEMEMBER)
      .retrieve();
  }

  /**
   * Retrieve a message from its id
   *
   * @param messageId The message id
   * @return The message text
   */
  @NewSpan
  public Optional<MessageInfo> getMessage(String messageId, SymphonySession session) {
    try {
      //TODO put /agent/v1/message/{id} in a constant
      SymphonyInboundMessage response = webClient.get()
        .uri(podConfiguration.getUrl() + GETMESSAGE, messageId)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .header("sessionToken", session.getSessionToken())
        .header("keyManagerToken", session.getKmToken())
        .attribute(BASE_URI, podConfiguration.getUrl())
        .attribute(BASE_PATH, GETMESSAGE)
        .retrieve()
        .bodyToMono(SymphonyInboundMessage.class)
        .block();

      return Optional.ofNullable(response)
        .map(r -> new MessageInfo()
          .message(r.getMessageText(symMessageParser))
          .messageId(r.getMessageId())
          .disclaimer(r.getDisclaimer()));
    } catch (Exception e) {
      logWebClientError(LOG, GETMESSAGE.replace("{messageId}", messageId), e);
      return Optional.empty();
    }
  }


  @NewSpan
  public Optional<SBEEventMessage> getEncryptedMessage(String messageId, SymphonySession session) {
    try {
      SBEEventMessage response = webClient.get()
      .uri(podConfiguration.getUrl() + GETMESSAGEENCRYPTED, messageId)
      .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .header("sessionToken", session.getSessionToken())
      .header("keyManagerToken", session.getKmToken())
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
  public String getAttachment(String streamId, String messageId, String fileId, SymphonySession session) {
    String response = webClient.get()
      .uri(podConfiguration.getUrl() + GETATTACHMENT, streamId, messageId, fileId)
      .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .header("sessionToken", session.getSessionToken())
      .header("keyManagerToken", session.getKmToken())
      .attribute(BASE_URI, podConfiguration.getUrl())
      .attribute(BASE_PATH, GETATTACHMENT)
      .retrieve()
      .bodyToMono(String.class)
      .block();

    return response;
  }

  public SBEEventMessage sendReplyMessage(SBEEventMessage message, SymphonySession session) throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

    String payload = objectMapper.writeValueAsString(Collections.singletonList(message));
    Object response = webClient.post()
      .uri(podConfiguration.getUrl() + REPLYTOMESSAGE)
      .contentType(MediaType.APPLICATION_JSON)
      .header("sessionToken", session.getSessionToken())
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
