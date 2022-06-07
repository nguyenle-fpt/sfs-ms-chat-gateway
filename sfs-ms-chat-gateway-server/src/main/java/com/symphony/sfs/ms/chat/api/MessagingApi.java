package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.chat.generated.model.MessageInfoWithCustomEntities;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessagesRequest;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessagesResponse;
import com.symphony.sfs.ms.chat.generated.model.SendMessageRequest;
import com.symphony.sfs.ms.chat.generated.model.SendSystemMessageRequest;
import com.symphony.sfs.ms.chat.generated.model.SetMessagesAsReadRequest;
import com.symphony.sfs.ms.chat.service.SymphonyMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.sleuth.annotation.ContinueSpan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
public class MessagingApi implements com.symphony.sfs.ms.chat.generated.api.MessagingApi {

  private final SymphonyMessageService symphonyMessageService;

  @Override
  @ContinueSpan
  public ResponseEntity<MessageInfoWithCustomEntities> sendMessage(SendMessageRequest request) {
    MessageInfoWithCustomEntities messageInfo = symphonyMessageService.sendMessage(request.getStreamId(), request.getFromSymphonyUserId(), request.getTenantId(), request.getFormatting(), request.getText(), request.getAttachments(), Boolean.TRUE.equals(request.isForwarded()),
      request.getReplyToMessageId(), request.isAttachmentReplySupported(), Optional.ofNullable(request.getReplyToAttachmentMessageIds()));
    return ResponseEntity.ok(messageInfo);
  }

  @Override
  public ResponseEntity<MessageInfoWithCustomEntities> sendSystemMessage(SendSystemMessageRequest request) {
    LOG.info("Send system message | streamId={}", request.getStreamId());
    MessageInfoWithCustomEntities messageInfo = symphonyMessageService.sendSystemMessage(request.getStreamId(), request.getFormatting(), request.getText(), request.getTitle(), request.getFromSymphonyUserId());
    return ResponseEntity.ok(messageInfo);
  }

  @Override
  @ContinueSpan
  public ResponseEntity<RetrieveMessagesResponse> retrieveMessages(@Valid RetrieveMessagesRequest body) {
    RetrieveMessagesResponse response = symphonyMessageService.retrieveMessages(body.getMessagesIds(), body.getSymphonyUserId());
    return ResponseEntity.ok(response);
  }

  @Override
  @ContinueSpan
  public ResponseEntity<Void> markMessagesAsRead(@Valid SetMessagesAsReadRequest setMessagesAsReadRequest) {
    symphonyMessageService.markMessagesAsRead(setMessagesAsReadRequest);
    return ResponseEntity.ok().build();
  }
}
