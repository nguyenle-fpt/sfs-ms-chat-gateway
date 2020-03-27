package com.symphony.sfs.ms.chat.service.symphony;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Object used in Symphony Admin User Management REST API
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SymphonyUserAvatar {

  private String size;
  private String url;
}
