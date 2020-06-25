package com.symphony.sfs.ms.chat.service.symphony;

import clients.symphony.api.constants.PodConstants;
import com.symphony.sfs.ms.starter.security.SessionManager;
import com.symphony.sfs.ms.starter.security.SessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.AdminUserInfo;
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
@RequiredArgsConstructor
public class AdminUserManagementService {

  private final SessionManager sessionManager;

  public Optional<SymphonyUser> createUser(String podUrl, SessionSupplier<SymphonySession> session, SymphonyUser user) {

    if (StringUtils.isBlank(podUrl)) {
      throw new IllegalArgumentException("No URL provided for Create User");
    }

    WebClient client = sessionManager.getWebClient(session);
    try {
      SymphonyUser response = blockWithRetries(client.post()
        .uri(podUrl + PodConstants.ADMINCREATEUSER)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .body(BodyInserters.fromValue(user))
        .retrieve()
        .bodyToMono(SymphonyUser.class));
      return Optional.ofNullable(response);
    } catch (Exception e) {
      logWebClientError(LOG, PodConstants.ADMINCREATEUSER, e);
    }
    return Optional.empty();
  }

  public Optional<AdminUserInfo> getUserInfo(String podUrl, SessionSupplier<SymphonySession> session, String userId) {

    if (StringUtils.isBlank(podUrl)) {
      throw new IllegalArgumentException("No URL provided for get user");
    }

    WebClient client = sessionManager.getWebClient(session);
    try {
      AdminUserInfo response = blockWithRetries(client.get()
        .uri(podUrl + PodConstants.GETUSERADMIN, userId)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .retrieve()
        .bodyToMono(AdminUserInfo.class));
      return Optional.ofNullable(response);
    } catch (Exception e) {
      logWebClientError(LOG, PodConstants.GETUSERADMIN, e);
    }
    return Optional.empty();
  }
}
