package com.symphony.sfs.ms.chat.service.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.symphony.sfs.ms.SfsAdminClient;
import com.symphony.sfs.ms.admin.generated.model.AdvisorUpdateRequest;
import com.symphony.sfs.ms.admin.generated.model.BlockedFileTypes;
import com.symphony.sfs.ms.admin.generated.model.CanChatResponse;
import com.symphony.sfs.ms.admin.generated.model.EmpList;
import com.symphony.sfs.ms.admin.generated.model.RoomLeftNotification;
import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.service.JwtTokenGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
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
  public Optional<CanChatResponse> canChat(String advisorSymphonyId, String federatedUserId, String entitlementType) {
    adminClient.getContactApi().getApiClient().setSfsAuthentication(jwtTokenGenerator.generateMicroserviceToken());
    return adminClient.getContactApi().canChat(advisorSymphonyId, federatedUserId, entitlementType);
  }

  @Override
  public void notifyLeaveRoom(String streamId, String requester, List<String> leavers) {
    adminClient.getRoomApi().getApiClient().setSfsAuthentication(jwtTokenGenerator.generateMicroserviceToken());

    RoomLeftNotification roomLeft = new RoomLeftNotification()
      .requester(requester)
      .leavers(leavers)
      .streamId(streamId);
    adminClient.getWebhookApi().usersLeftRoom(roomLeft);
  }

  @Override
  public void updateAdvisorInfo(AdvisorUpdateRequest advisorUpdateRequest) {
    adminClient.getRoomApi().getApiClient().setSfsAuthentication(jwtTokenGenerator.generateMicroserviceToken());
    adminClient.getWebhookApi().updateAdvisorInfo(advisorUpdateRequest);
  }

  @Override
  public Optional<BlockedFileTypes> getBlockedFileTypes(String streamId, String emp) {
    adminClient.getAttachmentConfigApi().getApiClient().setSfsAuthentication(jwtTokenGenerator.generateMicroserviceToken());
    return adminClient.getAttachmentConfigApi().getBlockedFilesTypes(streamId, emp);
  }
}
