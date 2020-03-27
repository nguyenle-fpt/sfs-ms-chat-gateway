package com.symphony.sfs.ms.chat.service;

import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.chat.datafeed.DatafeedListener;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;

import java.util.List;

public class ChannelService implements DatafeedListener {

  private ForwarderQueueConsumer forwarderQueueConsumer;

  public ChannelService(ForwarderQueueConsumer forwarderQueueConsumer) {
    this.forwarderQueueConsumer = forwarderQueueConsumer;
    this.forwarderQueueConsumer.registerDatafeedListener(this);
  }

  @Override
  public void onIMCreated(String streamId, List<Long> members, IUser initiator, boolean crosspod) {

  }
}
