package com.symphony.sfs.ms.chat.service.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

import java.util.List;

@Data
@AllArgsConstructor
public class SendMessageToEmpRequest {

  @NonNull
  private String streamId;

  @NonNull
  private String messageId;

  @NonNull
  private List<ChannelMember> channelMembers;

  @NonNull
  private String fromSymphonyUserId;

  @NonNull
  private Long timestamp;

  @NonNull
  private String text;

}
