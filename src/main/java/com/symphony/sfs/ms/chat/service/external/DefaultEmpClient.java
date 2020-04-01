package com.symphony.sfs.ms.chat.service.external;

import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.service.EmpMicroserviceResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.symphony.sfs.ms.starter.util.WebClientUtils.blockWithRetries;
import static com.symphony.sfs.ms.starter.util.WebClientUtils.logWebClientError;

@Component
@Slf4j
public class DefaultEmpClient implements EmpClient {

  private final WebClient webClient;
  private final EmpMicroserviceResolver empMicroserviceResolver;

  public DefaultEmpClient(WebClient webClient, EmpMicroserviceResolver empMicroserviceResolver) {
    this.webClient = webClient;
    this.empMicroserviceResolver = empMicroserviceResolver;
  }

  @Override
  public Optional<String> createChannel(String emp, String streamId, List<FederatedAccount> federatedUsers, String initiatorUserId, List<String> symphonyUserIds) {
    URI uri = empMicroserviceResolver.buildEmpMicroserviceUri(emp, CREATE_CHANNEL_ENDPOINT);
    List<ChannelMember> members = new ArrayList<>();
    federatedUsers.forEach(account -> members.add(ChannelMember.builder()
      .emailAddress(account.getEmailAddress())
      .phoneNumber(account.getPhoneNumber())
      .firstName(account.getFirstName())
      .lastName(account.getLastName())
      .federatedUserId(account.getFederatedUserId())
      .symphonyId(account.getSymphonyUserId())
      .isFederatedUser(true)
      .isInitiator(initiatorUserId.equals(account.getSymphonyUserId()))
      .build()
    ));

    symphonyUserIds.forEach(userId -> members.add(ChannelMember.builder()
      .symphonyId(userId)
      .isFederatedUser(false)
      .isInitiator(initiatorUserId.equals(userId))
      .build()
    ));

    return post(new CreateChannelRequest(streamId, members), uri, AsyncResponse.class)
      .map(AsyncResponse::getLeaseId);
  }


  private <I, O> Optional<O> post(I input, URI uri, Class<O> outputClass) {
    if (uri == null) {
      return Optional.empty();
    }

    try {
      O output = blockWithRetries(webClient.post()
        .uri(uri)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .bodyValue(input)
        .retrieve()
        .bodyToMono(outputClass));
      return Optional.of(output);
    } catch (Exception e) {
      logWebClientError(LOG, uri.toString(), e);
    }
    return Optional.empty();
  }

  private <I> Optional<ResponseEntity<Void>> post(I input, URI uri) {
    if (uri == null) {
      return Optional.empty();
    }
    try {
      ResponseEntity<Void> output = blockWithRetries(webClient.post()
        .uri(uri)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .bodyValue(input)
        .retrieve()
        .toBodilessEntity());
      return Optional.of(output);
    } catch (Exception e) {
      logWebClientError(LOG, uri.toString(), e);
    }
    return Optional.empty();
  }
}
