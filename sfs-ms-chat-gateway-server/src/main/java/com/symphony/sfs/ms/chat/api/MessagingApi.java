package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.chat.generated.model.RetrieveMessagesRequest;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessagesResponse;
import com.symphony.sfs.ms.chat.generated.model.SendMessageRequest;
import com.symphony.sfs.ms.chat.generated.model.SendMessageResponse;
import com.symphony.sfs.ms.chat.service.SymphonyMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

import static org.jsoup.nodes.Entities.escape;

@Slf4j
@RestController
@RequiredArgsConstructor
public class MessagingApi implements com.symphony.sfs.ms.chat.generated.api.MessagingApi {

  private final SymphonyMessageService symphonyMessageService;

  @Override
  public ResponseEntity<SendMessageResponse> sendMessage(SendMessageRequest request) {
    String messageContent = formatMessageMLV2(request.getText());

    if (request.getFormatting() == null) {
      String message = "<messageML>" + messageContent + "</messageML>";
      symphonyMessageService.sendRawMessage(request.getStreamId(), request.getFromSymphonyUserId(), message);
    } else {
      switch (request.getFormatting()) {
        case SIMPLE:
          symphonyMessageService.sendSimpleMessage(request.getStreamId(), request.getFromSymphonyUserId(), messageContent);
          break;
        case NOTIFICATION:
          symphonyMessageService.sendNotificationMessage(request.getStreamId(), request.getFromSymphonyUserId(), messageContent);
          break;
        case INFO:
          symphonyMessageService.sendInfoMessage(request.getStreamId(), request.getFromSymphonyUserId(), messageContent);
          break;
        case ALERT:
          symphonyMessageService.sendAlertMessage(request.getStreamId(), request.getFromSymphonyUserId(), messageContent);
          break;
      }
    }
    SendMessageResponse response = new SendMessageResponse();
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<RetrieveMessagesResponse> retrieveMessages(@Valid RetrieveMessagesRequest body) {
    RetrieveMessagesResponse response = symphonyMessageService.retrieveMessages(body.getMessagesIds(), body.getSymphonyUserId());
    return ResponseEntity.ok(response);
  }

  /**
   * Escape special characters before sending to Symphony.
   *
   * @param text The text to send
   * @return The text well formatted
   */
  private String formatMessageMLV2(String text) {
    String res = escape(text);
    res = res.replace("\"", "&quot;");
    res = res.replace("#", "&#35;");
    res = res.replace("$", "&#36;");

    return res;
  }
}
