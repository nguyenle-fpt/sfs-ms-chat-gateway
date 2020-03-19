package com.symphony.sfs.ms.chat.datafeed;

import com.symphony.oss.models.chat.canon.facade.IUser;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MultiDatafeedListener implements DatafeedListener {
  private Set<DatafeedListener> listeners = new HashSet<>();

  public void onIMCreated(String streamId, List<Long> members, IUser initiator, boolean crosspod) {
    listeners.forEach(l -> l.onIMCreated(streamId, members, initiator, crosspod));
  }

  public void onConnectionRequested(IUser requesting, IUser requested) {
    listeners.forEach(l -> l.onConnectionRequested(requesting, requested));
  }

  public void onConnectionRefused(IUser requesting, IUser requested) {
    listeners.forEach(l -> l.onConnectionRefused(requesting, requested));
  }

  public void onConnectionAccepted(IUser requesting, IUser requested) {
    listeners.forEach(l -> l.onConnectionAccepted(requesting, requested));
  }

  public void onIMMessage(String streamId, String messageId, Long timestamp, String message) {
    listeners.forEach(l -> l.onIMMessage(streamId, messageId, timestamp, message));
  }

  public void register(DatafeedListener listener) {
    listeners.add(listener);
  }

  public void unregister(DatafeedListener listener) {
    listeners.remove(listener);
  }

}
