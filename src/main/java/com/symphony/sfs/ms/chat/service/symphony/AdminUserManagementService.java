package com.symphony.sfs.ms.chat.service.symphony;

import clients.symphony.api.constants.PodConstants;
import com.symphony.sfs.ms.starter.symphony.auth.UserSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;

import static com.symphony.sfs.ms.starter.util.WebClientUtils.blockWithRetries;
import static com.symphony.sfs.ms.starter.util.WebClientUtils.logWebClientError;

/**
 * Wrapper around Symphony Admin User Management REST API:
 * <p>
 * See https://developers.symphony.com/restapi/reference#user-attributes
 */
@Service
@Lazy
@Slf4j
public class AdminUserManagementService {

  private final WebClient webClient;

  public AdminUserManagementService(WebClient webClient) {
    this.webClient = webClient;
  }

  public Optional<SymphonyUser> createUser(SymphonyUser user, String podUrl, UserSession botSession) {

    if (StringUtils.isBlank(podUrl)) {
      throw new IllegalArgumentException("No URL provided for Create User");
    }

    try {
      SymphonyUser response = blockWithRetries(webClient.post()
        .uri(podUrl + PodConstants.ADMINCREATEUSER)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .header("sessionToken", botSession.getSessionToken())
        .body(BodyInserters.fromValue(user))
        .retrieve()
        .bodyToMono(SymphonyUser.class));
      return Optional.of(response);
    } catch (Exception e) {
      logWebClientError(LOG, PodConstants.ADMINCREATEUSER, e);
    }
    return Optional.empty();
  }
}
