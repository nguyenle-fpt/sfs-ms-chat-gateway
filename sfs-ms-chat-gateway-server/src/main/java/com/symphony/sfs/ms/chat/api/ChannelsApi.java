package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.chat.generated.model.CreateChannelRequest;
import com.symphony.sfs.ms.chat.generated.model.CreateChannelResponse;
import com.symphony.sfs.ms.chat.service.ChannelService;
import com.symphony.sfs.ms.chat.service.FederatedAccountService;
import lombok.extern.slf4j.Slf4j;
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
  public ResponseEntity<CreateChannelResponse> createChannel(@Valid CreateChannelRequest request) {
    String channelId = federatedAccountService.createChannel(request);
    CreateChannelResponse response = new CreateChannelResponse()
      .channelId(channelId);
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<Void> deleteBySymphonyId(@NotBlank String emp, @NotBlank String symphonyId) {
    channelService.deleteChannelByFederatedUserSymphonyIdAndEmp(symphonyId, emp);
    return ResponseEntity.ok().build();
  }
}
