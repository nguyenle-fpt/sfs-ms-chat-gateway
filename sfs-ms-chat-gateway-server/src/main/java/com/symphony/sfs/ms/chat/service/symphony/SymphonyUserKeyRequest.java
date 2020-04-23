package com.symphony.sfs.ms.chat.service.symphony;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Object used in Symphony Admin User Management REST API
 * <p>
 * See https://developers.symphony.com/restapi/reference#userkeyrequest-object
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SymphonyUserKeyRequest {

  // Possible values for the action field
  public static final String ACTION_SAVE = "SAVE";
  public static final String ACTION_REVOKE = "REVOKE";
  public static final String ACTION_EXTEND = "EXTEND";

  private String key;
  private Long expirationDate;
  private String action;
}
