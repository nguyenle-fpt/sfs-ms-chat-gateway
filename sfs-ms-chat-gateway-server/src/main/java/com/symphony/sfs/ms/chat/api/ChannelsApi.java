package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.chat.generated.model.CreateChannelRequest;
import com.symphony.sfs.ms.chat.generated.model.CreateChannelResponse;
import com.symphony.sfs.ms.chat.service.ChannelService;
import com.symphony.sfs.ms.chat.service.FederatedAccountService;
import org.springframework.cloud.sleuth.annotation.ContinueSpan;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import lombok.extern.slf4j.Slf4j;
import org.apache.log4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

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
  @ContinueSpan
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

}
