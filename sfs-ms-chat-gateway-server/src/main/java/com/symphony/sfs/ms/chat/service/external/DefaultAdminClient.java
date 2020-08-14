package com.symphony.sfs.ms.chat.service.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.symphony.sfs.ms.SfsAdminClient;
import com.symphony.sfs.ms.admin.generated.model.CanChatResponse;
import com.symphony.sfs.ms.admin.generated.model.EmpList;
import com.symphony.sfs.ms.admin.generated.model.EntitlementResponse;
import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.service.JwtTokenGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;

@Component
@Slf4j
public class DefaultAdminClient implements AdminClient {

  private final JwtTokenGenerator jwtTokenGenerator;
  private final SfsAdminClient adminClient;

  public DefaultAdminClient(WebClient webClient, ObjectMapper objectMapper, ChatConfiguration chatConfiguration, JwtTokenGenerator jwtTokenGenerator) {
    this.jwtTokenGenerator = jwtTokenGenerator;
    this.adminClient = new SfsAdminClient(chatConfiguration.getMsAdminUrl(), webClient, objectMapper);
  }

  @Override
  public EmpList getEmpList() {
    adminClient.getEmpApi().getApiClient().setSfsAuthentication(jwtTokenGenerator.generateMicroserviceToken());
    return adminClient.getEmpApi().getEmpList().orElse(new EmpList());
  }

  @Override
  public Optional<EntitlementResponse> getEntitlementAccess(String symphonyId, String entitlementType) {
    adminClient.getEntitlementsApi().getApiClient().setSfsAuthentication(jwtTokenGenerator.generateMicroserviceToken());
    return adminClient.getEntitlementsApi().getInternalEntitlement(symphonyId, entitlementType);
  }

  @Override
  public Optional<CanChatResponse> canChat(String advisorSymphonyId, String federatedUserId, String entitlementType) {
    adminClient.getContactApi().getApiClient().setSfsAuthentication(jwtTokenGenerator.generateMicroserviceToken());
    return adminClient.getContactApi().canChat(advisorSymphonyId, federatedUserId, entitlementType);
  }
}
