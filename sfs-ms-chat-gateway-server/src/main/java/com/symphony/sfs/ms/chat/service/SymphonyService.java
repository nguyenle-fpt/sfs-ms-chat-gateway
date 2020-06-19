package com.symphony.sfs.ms.chat.service;

import clients.symphony.api.constants.PodConstants;
import com.symphony.sfs.ms.chat.generated.model.MessageInfo;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.symphony.auth.UserSession;
import com.symphony.sfs.ms.starter.symphony.stream.SymMessageParser;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyInboundMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;

import static com.symphony.sfs.ms.starter.util.WebClientUtils.logWebClientError;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Service
@Slf4j
@RequiredArgsConstructor
public class SymphonyService {

  private final WebClient webClient;
  private final PodConfiguration podConfiguration;
  private final SymMessageParser symMessageParser;

  public void removeMemberFromRoom(String streamId, UserSession session) {
    webClient.post()
      .uri(podConfiguration.getUrl() + PodConstants.REMOVEMEMBER, streamId)
      .header(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_VALUE)
      .header("sessionToken", session.getSessionToken())
      .header("keyManagerToken", session.getKmToken())
      .retrieve();
  }

  /**
   * Retrieve a message from its id
   *
   * @param messageId The message id
   * @return The message text
   */
  public Optional<MessageInfo> getMessage(String messageId, UserSession botSession, String podUrl) {
    try {
      //TODO put /agent/v1/message/{id} in a constant
      SymphonyInboundMessage response = webClient.get()
        .uri(podUrl + "/agent/v1/message/" + messageId)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .header("sessionToken", botSession.getSessionToken())
        .header("keyManagerToken", botSession.getKmToken())
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
