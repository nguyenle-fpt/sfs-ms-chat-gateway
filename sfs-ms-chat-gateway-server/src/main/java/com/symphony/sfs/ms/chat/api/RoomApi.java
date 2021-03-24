package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.chat.generated.model.RoomMemberRemoveRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomMemberRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomMemberResponse;
import com.symphony.sfs.ms.chat.generated.model.RoomRemoveRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomResponse;
import com.symphony.sfs.ms.chat.generated.model.SendRoomMembersRequest;
import com.symphony.sfs.ms.chat.generated.model.UpdateRoomActivityRequest;
import com.symphony.sfs.ms.chat.generated.model.UpdateRoomActivityResponse;
import com.symphony.sfs.ms.chat.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
  public ResponseEntity<UpdateRoomActivityResponse> updateRoomActivity(String streamId, UpdateRoomActivityRequest updateRoomActivityRequest) {
    LOG.info("Update Room Activity | streamId={} setActive={}", streamId, updateRoomActivityRequest.isSetActive());
    return ResponseEntity.ok(roomService.updateRoomActivity(streamId, updateRoomActivityRequest));
  }

  @Override
  public ResponseEntity<Void> sendRoomMembersListToEmpUser(String streamId, SendRoomMembersRequest sendRoomMembersRequest) {
    LOG.info("Send room members list | streamId={} symphonyId={} isUserJoining={}", streamId, sendRoomMembersRequest.getSymphonyId(), sendRoomMembersRequest.isUserJoining());
    roomService.sendRoomMembersListToEmpUser(streamId, sendRoomMembersRequest.getSymphonyId(), sendRoomMembersRequest.isUserJoining());
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<RoomMemberResponse> addRoomMember(String streamId, RoomMemberRequest roomMemberRequest) {
    LOG.info("Add Room Member | streamId={} symphonyId={} isFederatedUser={} clientPodId={}", streamId, roomMemberRequest.getSymphonyId(), roomMemberRequest.isFederatedUser(), roomMemberRequest.getClientPodId());
    return ResponseEntity.ok(roomService.addRoomMember(streamId, roomMemberRequest));
  }

  @Override
  public ResponseEntity<Void> removeMember(String streamId, RoomMemberRemoveRequest body) {
    roomService.removeMember(streamId, body.getSymphonyId(), body.getEmp(), body.isFederatedUser(), body.isRemoveChannel());
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<Void> deleteRoom(String streamId, RoomRemoveRequest body) {
    roomService.deleteRoom(streamId, body);
    return ResponseEntity.ok().build();
  }
}
