package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.chat.generated.model.RetrieveMessagesRequest;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessagesResponse;
import com.symphony.sfs.ms.chat.generated.model.SendMessageRequest;
import com.symphony.sfs.ms.chat.generated.model.SendMessageResponse;
import com.symphony.sfs.ms.chat.generated.model.SendSystemMessageRequest;
import com.symphony.sfs.ms.chat.service.SymphonyMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.sleuth.annotation.ContinueSpan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Slf4j
@RestController
@RequiredArgsConstructor
public class MessagingApi implements com.symphony.sfs.ms.chat.generated.api.MessagingApi {

  private final SymphonyMessageService symphonyMessageService;

  @Override
  @ContinueSpan
  public ResponseEntity<SendMessageResponse> sendMessage(SendMessageRequest request) {
    String symphonyMessageId = symphonyMessageService.sendMessage(request.getStreamId(), request.getFromSymphonyUserId(), request.getFormatting(), request.getText(), request.getAttachments());
    return ResponseEntity.ok(new SendMessageResponse().id(symphonyMessageId));
  }

  @Override
  public ResponseEntity<SendMessageResponse> sendSystemMessage(SendSystemMessageRequest request) {
    LOG.info("Send system message | streamId={}", request.getStreamId());
    String symphonyMessageId = symphonyMessageService.sendSystemMessage(request.getStreamId(), request.getFormatting(), request.getText(), request.getTitle(), request.getFromSymphonyUserId());
    return ResponseEntity.ok(new SendMessageResponse().id(symphonyMessageId));
  }

  @Override
  @ContinueSpan
  public ResponseEntity<RetrieveMessagesResponse> retrieveMessages(@Valid RetrieveMessagesRequest body) {
    RetrieveMessagesResponse response = symphonyMessageService.retrieveMessages(body.getMessagesIds(), body.getSymphonyUserId());
    return ResponseEntity.ok(response);
  }
}
