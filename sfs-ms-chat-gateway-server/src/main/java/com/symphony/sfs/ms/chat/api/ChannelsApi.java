package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.chat.generated.model.DeleteChannelsRequest;
import com.symphony.sfs.ms.chat.generated.model.DeleteChannelsResponse;
import com.symphony.sfs.ms.chat.generated.model.RetrieveChannelResponse;
import com.symphony.sfs.ms.chat.model.Channel;
import com.symphony.sfs.ms.chat.service.ChannelService;
import lombok.extern.slf4j.Slf4j;
import org.apache.log4j.MDC;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class ChannelsApi implements com.symphony.sfs.ms.chat.generated.api.ChannelsApi {

  private ChannelService channelService;

  public ChannelsApi(ChannelService channelService) {
    this.channelService = channelService;
  }

  @Override
  @NewSpan
  public ResponseEntity<DeleteChannelsResponse> deleteChannels(DeleteChannelsRequest body) {
    LOG.info("delete channels");
    return ResponseEntity.ok(channelService.deleteChannels(body.getChannels()));
  }

  @Override
  @NewSpan
  public ResponseEntity<RetrieveChannelResponse> retrieveChannel(String advisorSymphonyId, String federatedUserId, String emp) {
    MDC.put("federatedUserId", federatedUserId);
    MDC.put("emp", emp);
    MDC.put("advisor", advisorSymphonyId);
    LOG.info("retrieve channel");
    Channel channel = channelService.retrieveChannelOrFail(advisorSymphonyId, federatedUserId, emp);
    RetrieveChannelResponse retrieveChannelResponse = new RetrieveChannelResponse()
      .streamId(channel.getStreamId())
      .advisorSymphonyId(channel.getAdvisorSymphonyId())
      .emp(channel.getEmp())
      .federatedUserId(channel.getFederatedUserId());

    return ResponseEntity.ok(retrieveChannelResponse);
  }
}
