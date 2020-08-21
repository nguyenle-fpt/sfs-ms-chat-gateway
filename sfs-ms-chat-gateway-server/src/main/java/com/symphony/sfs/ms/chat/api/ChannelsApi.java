package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.chat.generated.model.CreateChannelRequest;
import com.symphony.sfs.ms.chat.generated.model.CreateChannelResponse;
import com.symphony.sfs.ms.chat.generated.model.RetrieveChannelResponse;
import com.symphony.sfs.ms.chat.model.Channel;
import com.symphony.sfs.ms.chat.service.ChannelService;
import com.symphony.sfs.ms.chat.service.FederatedAccountService;
import lombok.extern.slf4j.Slf4j;
import org.apache.log4j.MDC;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Slf4j
@RestController
public class ChannelsApi implements com.symphony.sfs.ms.chat.generated.api.ChannelsApi {

  private FederatedAccountService federatedAccountService;
  private ChannelService channelService;

  public ChannelsApi(FederatedAccountService federatedAccountService, ChannelService channelService) {
    this.federatedAccountService = federatedAccountService;
    this.channelService = channelService;
  }

  @Override
  @NewSpan
  public ResponseEntity<CreateChannelResponse> createChannel(@Valid CreateChannelRequest request) {
    MDC.put("federatedUserId", request.getFederatedUserId());
    MDC.put("emp", request.getEmp());
    MDC.put("advisor", request.getAdvisorUserId());
    LOG.info("Create channel");
    String channelId = federatedAccountService.createChannel(request);
    CreateChannelResponse response = new CreateChannelResponse()
      .channelId(channelId);
    return ResponseEntity.ok(response);
  }

  @Override
  @NewSpan
  public ResponseEntity<Void> deleteChannel(String advisorSymphonyId, String federatedUserId, String emp) {
    MDC.put("federatedUserId", federatedUserId);
    MDC.put("emp", emp);
    MDC.put("advisor", advisorSymphonyId);
    LOG.info("delete channel");
    channelService.deleteChannel(advisorSymphonyId, federatedUserId, emp);
    return ResponseEntity.ok().build();
  }

  @Override
  @NewSpan
  public ResponseEntity<RetrieveChannelResponse> retrieveChannel(String advisorSymphonyId, String federatedUserId, String emp) {
    MDC.put("federatedUserId", federatedUserId);
    MDC.put("emp", emp);
    MDC.put("advisor", advisorSymphonyId);
    LOG.info("retrieve channel");
    Channel channel = channelService.retrieveChannel(advisorSymphonyId, federatedUserId, emp);
    RetrieveChannelResponse retrieveChannelResponse = new RetrieveChannelResponse()
      .streamId(channel.getStreamId())
      .advisorSymphonyId(channel.getAdvisorSymphonyId())
      .emp(channel.getEmp())
      .federatedUserId(channel.getFederatedUserId());

    return ResponseEntity.ok(retrieveChannelResponse);
  }
}
