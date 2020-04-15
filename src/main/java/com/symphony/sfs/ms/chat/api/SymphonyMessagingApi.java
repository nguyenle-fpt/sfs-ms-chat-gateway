package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.chat.generated.model.SendMessageRequest;
import com.symphony.sfs.ms.chat.generated.model.SendMessageResponse;
import com.symphony.sfs.ms.chat.service.SymphonyMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SymphonyMessagingApi implements com.symphony.sfs.ms.chat.generated.api.SymphonyMessagingApi {

  private final SymphonyMessageService symphonyMessageService;

  @Override
  public ResponseEntity<SendMessageResponse> sendMessage(SendMessageRequest request) {

    symphonyMessageService.sendRawMessage(request.getStreamId(), request.getFromSymphonyUserId(), request.getText());

    SendMessageResponse response = new SendMessageResponse();
    return ResponseEntity.ok(response);
  }
}
