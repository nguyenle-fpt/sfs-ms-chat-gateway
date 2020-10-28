package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.chat.generated.model.RoomMemberRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomMemberResponse;
import com.symphony.sfs.ms.chat.generated.model.RoomRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomResponse;
import com.symphony.sfs.ms.chat.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.log4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class RoomApi implements com.symphony.sfs.ms.chat.generated.api.RoomApi {

  private final RoomService roomService;

  @Override
  public ResponseEntity<RoomResponse> createRoom(RoomRequest roomRequest) {
    LOG.info("Create Room | roomName={}", roomRequest.getRoomName());
    return ResponseEntity.ok(roomService.createRoom(roomRequest));
  }

  @Override
  public ResponseEntity<RoomMemberResponse> addRoomMember(String streamId, RoomMemberRequest roomMemberRequest) {
    LOG.info("Add Room Member | streamId={} symphonyId={} isFederatedUser={} clientPodId={}", streamId, roomMemberRequest.getSymphonyId(), roomMemberRequest.isFederatedUser(), roomMemberRequest.getClientPodId());
    return ResponseEntity.ok(roomService.addRoomMember(streamId, roomMemberRequest));
  }
}
