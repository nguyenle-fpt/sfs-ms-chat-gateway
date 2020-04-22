package com.symphony.sfs.ms.chat.util;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 *
 */
@Builder
@Getter
@ToString
public class SymphonySystemMessage {

  private final String content;
  private String title;
  private String description;
}
