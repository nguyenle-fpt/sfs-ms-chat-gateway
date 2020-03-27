package com.symphony.sfs.ms.chat.service.symphony;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Object used in Symphony Admin User Management REST API
 * <p>
 * See https://developers.symphony.com/restapi/reference#user-attributes
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SymphonyUserSystemAttributes {

  private Long id;
  private String status;
  private String createdBy;
  private Long createdDate;
  private Long lastUpdatedDate;
  private Long lastLoginDate;

}
