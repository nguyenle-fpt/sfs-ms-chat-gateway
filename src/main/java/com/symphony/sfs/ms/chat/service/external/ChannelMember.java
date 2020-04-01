package com.symphony.sfs.ms.chat.service.external;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChannelMember {
  private String phoneNumber;
  private String emailAddress;
  private String firstName;
  private String lastName;
  private String symphonyId;
  private Boolean isFederatedUser;
  private Boolean isInitiator;
  private String federatedUserId;
}
