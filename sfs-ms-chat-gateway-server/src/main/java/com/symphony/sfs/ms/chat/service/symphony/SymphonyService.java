package com.symphony.sfs.ms.chat.service.symphony;

import clients.symphony.api.constants.PodConstants;
import com.symphony.sfs.ms.chat.generated.model.MessageInfo;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.SymMessageParser;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyInboundMessage;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

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

  @NewSpan
  public void removeMemberFromRoom(String streamId, SymphonySession session) {
    webClient.post()
      .uri(podConfiguration.getUrl() + PodConstants.REMOVEMEMBER, streamId)
      .header(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_VALUE)
      .header("sessionToken", session.getSessionToken())
      .header("keyManagerToken", session.getKmToken())
      .attribute(BASE_URI, podConfiguration.getUrl())
      .attribute(BASE_PATH,  PodConstants.REMOVEMEMBER)
      .retrieve();
  }

  /**
   * Retrieve a message from its id
   *
   * @param messageId The message id
   * @return The message text
   */
  @NewSpan
  public Optional<MessageInfo> getMessage(String messageId, SymphonySession botSession, String podUrl) {
    try {
      //TODO put /agent/v1/message/{id} in a constant
      SymphonyInboundMessage response = webClient.get()
        .uri(podUrl + "/agent/v1/message/" + messageId)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .header("sessionToken", botSession.getSessionToken())
        .header("keyManagerToken", botSession.getKmToken())
        .attribute(BASE_URI, podUrl)
        .attribute(BASE_PATH,  "/agent/v1/message/")
        .retrieve()
        .bodyToMono(SymphonyInboundMessage.class)
        .block();

      return Optional.ofNullable(response)
        .map(r -> new MessageInfo()
          .message(r.getMessageText(symMessageParser))
          .messageId(r.getMessageId())
          .disclaimer(r.getDisclaimer()));
    } catch (Exception e) {
      logWebClientError(LOG, "/agent/v1/message/" + messageId, e);
      return Optional.empty();
    }
  }
}
