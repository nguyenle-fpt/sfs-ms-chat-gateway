package com.symphony.sfs.ms.chat.service.symphony;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class UserStatus {
  @Getter
  private final String status;

  public static UserStatus DISABLED = new UserStatus("DISABLED");
  public static UserStatus ENABLED = new UserStatus("ENABLED");
}
