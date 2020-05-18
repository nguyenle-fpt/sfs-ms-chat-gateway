package com.symphony.sfs.ms.chat.service;

import clients.symphony.api.constants.PodConstants;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.symphony.auth.UserSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Service
@RequiredArgsConstructor
public class RoomService {

  private final WebClient webClient;
  private final PodConfiguration podConfiguration;

  public void removeMemberFromRoom(String streamId, UserSession session) {
    webClient.post()
      .uri(podConfiguration.getUrl() + PodConstants.REMOVEMEMBER, streamId)
      .header(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_VALUE)
      .header("sessionToken", session.getSessionToken())
      .header("keyManagerToken", session.getKmToken())
      .retrieve();

  }
}
