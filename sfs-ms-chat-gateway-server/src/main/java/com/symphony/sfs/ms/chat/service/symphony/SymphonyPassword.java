package com.symphony.sfs.ms.chat.service.symphony;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Object used in Symphony Admin User Management REST API
 * <p>
 * See https://developers.symphony.com/restapi/reference#password-object
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SymphonyPassword {

  private String hhSalt;
  private String hhPassword;
  private String khSalt;
  private String khPassword;

}
