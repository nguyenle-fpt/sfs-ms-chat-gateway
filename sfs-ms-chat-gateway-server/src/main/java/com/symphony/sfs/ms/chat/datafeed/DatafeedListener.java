package com.symphony.sfs.ms.chat.datafeed;

import com.symphony.oss.models.chat.canon.facade.IUser;
import model.InboundMessage;
import model.Stream;
import model.events.RoomCreated;
import model.events.RoomDeactivated;
import model.events.RoomMemberDemotedFromOwner;
import model.events.RoomMemberPromotedToOwner;
import model.events.RoomUpdated;
import model.events.UserLeftRoom;

import java.util.List;

public interface DatafeedListener {

  default void onIMCreated(String streamId, List<String> members, IUser initiator, boolean crosspod) {
  }

  default void onConnectionRequested(IUser requesting, IUser requested) {
  }

  default void onConnectionRefused(IUser requesting, IUser requested) {
  }

  default void onConnectionAccepted(IUser requesting, IUser requested) {
  }

  default void onConnectionDeleted(IUser requesting, IUser requested) {
  }

  default void onIMMessage(GatewaySocialMessage gatewaySocialMessage) {
  }

  default void onRoomMessage(InboundMessage inboundMessage) {
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

  default void onUserJoinedRoom(String streamId, List<String> members, IUser fromSymphonyUser) {
  }

  default void onUserLeftRoom(String streamId, IUser requestor, List<IUser> leavingUsers) {
  }

  /*default void onConnectionAccepted(User user) {
  }

  default void onConnectionRequested(User user) {
  }*/
}
