package com.symphony.sfs.ms.chat.service.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.symphony.sfs.ms.SfsAdminClient;
import com.symphony.sfs.ms.admin.generated.model.EmpList;
import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.exception.AdvisorNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.UserInfo;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Optional;

import static com.symphony.sfs.ms.starter.util.WebClientUtils.blockWithRetries;
import static com.symphony.sfs.ms.starter.util.WebClientUtils.logWebClientError;

@Component
@Slf4j
public class DefaultAdminClient implements AdminClient {

  private final WebClient webClient;
  private final ObjectMapper objectMapper;
  private final ChatConfiguration chatConfiguration;
  private final SfsAdminClient adminClient;

  public DefaultAdminClient(WebClient webClient, ObjectMapper objectMapper, ChatConfiguration chatConfiguration) {
    this.webClient = webClient;
    this.objectMapper = objectMapper;
    this.chatConfiguration = chatConfiguration;
    this.adminClient = new SfsAdminClient(chatConfiguration.getMsAdminUrl(), webClient, objectMapper);
  }

  @Override
  public Optional<UserInfo> getAdvisor(String userId) throws AdvisorNotFoundException {

    try {
      UserInfo userInfo = blockWithRetries(webClient.get()
        .uri(chatConfiguration.getMsAdminUrl() + GET_ADVISOR_ENDPOINT, userId)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .retrieve()
        .bodyToMono(UserInfo.class));
      return Optional.of(userInfo);
    } catch (WebClientResponseException e) {
      if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
        throw new AdvisorNotFoundException(userId);
      }
      logWebClientError(LOG, GET_ADVISOR_ENDPOINT, e);
    } catch (Exception e) {
      logWebClientError(LOG, GET_ADVISOR_ENDPOINT, e);
    }

    return Optional.empty();
  }

  @Override
  public EmpList getEmpList() {
    return adminClient.getEmpApi().getEmpList().orElse(new EmpList());
  }
}
