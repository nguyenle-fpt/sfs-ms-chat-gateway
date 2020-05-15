package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.chat.generated.model.CreateAccountResponse;
import com.symphony.sfs.ms.chat.generated.model.CreateChannelRequest;
import com.symphony.sfs.ms.chat.generated.model.CreateChannelResponse;
import com.symphony.sfs.ms.chat.service.FederatedAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Slf4j
@RestController
public class ChannelsApi implements com.symphony.sfs.ms.chat.generated.api.ChannelsApi {

  private FederatedAccountService federatedAccountService;

  public ChannelsApi(FederatedAccountService federatedAccountService) {
    this.federatedAccountService = federatedAccountService;
  }

  @Override
  public ResponseEntity<CreateChannelResponse> createChannel(@Valid CreateChannelRequest request) {
    String channelId = federatedAccountService.createChannel(request);
    CreateChannelResponse response = new CreateChannelResponse()
      .channelId(channelId);
    return ResponseEntity.ok(response);
  }
}
