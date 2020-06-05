package com.symphony.sfs.ms.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.symphony.sfs.ms.chat.generated.api.AccountsApi;
import com.symphony.sfs.ms.chat.generated.api.ChannelsApi;
import com.symphony.sfs.ms.chat.generated.api.EmpApi;
import com.symphony.sfs.ms.chat.generated.api.MessagingApi;
import com.symphony.sfs.ms.chat.generated.client.ApiClient;
import lombok.Getter;
import org.springframework.web.reactive.function.client.WebClient;

@Getter
public class SfsChatGatewayClient {

  private final WebClient webClient;
  private final ObjectMapper objectMapper;
  private final ApiClient apiClient;

  private final AccountsApi accountsApi;
  private final ChannelsApi channelsApi;
  private final MessagingApi messagingApi;
  private final EmpApi empApi;

  public SfsChatGatewayClient(String baseUri, WebClient webClient, ObjectMapper objectMapper) {
    this.webClient = webClient;
    this.objectMapper = objectMapper;

    this.apiClient = new ApiClient(objectMapper, webClient);
    this.accountsApi = new AccountsApi(baseUri, apiClient);
    this.channelsApi = new ChannelsApi(baseUri, apiClient);
    this.messagingApi = new MessagingApi(baseUri, apiClient);
    this.empApi = new EmpApi(baseUri, apiClient);
  }
}
