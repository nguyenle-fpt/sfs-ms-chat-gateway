package com.symphony.sfs.ms.chat.datafeed;

import com.symphony.oss.models.chat.canon.facade.IUser;

import java.util.List;

public interface DatafeedListener {

  default void onIMCreated(String streamId, List<Long> members, IUser initiator, boolean crosspod) {
  }

  default void onConnectionRequested(IUser requesting, IUser requested) {
  }

  default void onConnectionRefused(IUser requesting, IUser requested) {
  }

  default void onConnectionAccepted(IUser requesting, IUser requested) {
  }

  default void onIMMessage(String streamId, String messageId, Long timestamp, String message) {
  }

  /*default void onRoomMessage(InboundMessage inboundMessage) {
  }

  default void onRoomCreated(RoomCreated roomCreated) {
  }

  default void onRoomDeactivated(RoomDeactivated roomDeactivated) {
  }

  default void onRoomMemberDemotedFromOwner(RoomMemberDemotedFromOwner roomMemberDemotedFromOwner) {
  }

  default void onRoomMemberPromotedToOwner(RoomMemberPromotedToOwner roomMemberPromotedToOwner) {
  }

  default void onRoomReactivated(Stream stream) {
  }

  default void onRoomUpdated(RoomUpdated roomUpdated) {
  }

  default void onUserJoinedRoom(UserJoinedRoom userJoinedRoom) {
  }

  default void onUserLeftRoom(UserLeftRoom userLeftRoom) {
  }

  default void onConnectionAccepted(User user) {
  }

  default void onConnectionRequested(User user) {
  }*/
}
