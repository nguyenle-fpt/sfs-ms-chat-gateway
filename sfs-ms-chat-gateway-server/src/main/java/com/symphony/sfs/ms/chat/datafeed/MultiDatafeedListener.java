package com.symphony.sfs.ms.chat.datafeed;

import com.symphony.oss.models.chat.canon.facade.IUser;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MultiDatafeedListener implements DatafeedListener {
  private Set<DatafeedListener> listeners = new HashSet<>();

  public void onIMCreated(String streamId, List<String> members, IUser initiator, boolean crosspod) {
    listeners.forEach(l -> l.onIMCreated(streamId, members, initiator, crosspod));
  }

  public void onConnectionRequested(IUser requesting, IUser requested) {
    listeners.forEach(l -> l.onConnectionRequested(requesting, requested));
  }

  public void onConnectionRefused(IUser requesting, IUser requested) {
    listeners.forEach(l -> l.onConnectionRefused(requesting, requested));
  }

  public void onConnectionDeleted(IUser requesting, IUser requested) {
    listeners.forEach(l -> l.onConnectionDeleted(requesting, requested));
  }

  public void onConnectionAccepted(IUser requesting, IUser requested) {
    listeners.forEach(l -> l.onConnectionAccepted(requesting, requested));
  }

  public void onIMMessage(String streamId, String messageId, IUser fromSymphonyUser, List<String> members, Long timestamp, String message) {
    listeners.forEach(l -> l.onIMMessage(streamId, messageId, fromSymphonyUser, members, timestamp, message));
  }

  public void register(DatafeedListener listener) {
    listeners.add(listener);
  }

  public void unregister(DatafeedListener listener) {
    listeners.remove(listener);
  }

  public void onUserJoinedRoom(String streamId, List<String> members, IUser initiator) {
    listeners.forEach(l -> l.onUserJoinedRoom(streamId, members, initiator));
  }

}
