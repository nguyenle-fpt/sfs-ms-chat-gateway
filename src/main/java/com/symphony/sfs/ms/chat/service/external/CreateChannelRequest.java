package com.symphony.sfs.ms.chat.service.external;

import lombok.Data;
import lombok.NonNull;

import java.util.List;

@Data
public class CreateChannelRequest {

  @NonNull
  private String streamId;

  @NonNull
  private List<ChannelMember> members;

}
