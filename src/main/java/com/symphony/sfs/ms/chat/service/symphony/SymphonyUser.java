package com.symphony.sfs.ms.chat.service.symphony;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Object used in Symphony Admin User Management REST API
 * <p>
 * See https://developers.symphony.com/restapi/reference#create-user-v2
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SymphonyUser {

  public static final String ROLE_ADMINISTRATOR = "ADMINISTRATOR";
  public static final String ROLE_COMPLIANCE_OFFICER = "COMPLIANCE_OFFICER";
  public static final String ROLE_CONTENT_MANAGEMENT = "CONTENT_MANAGEMENT";
  public static final String ROLE_INDIVIDUAL = "INDIVIDUAL";
  public static final String ROLE_L1_SUPPORT = "L1_SUPPORT";
  public static final String ROLE_L2_SUPPORT = "L2_SUPPORT";
  public static final String ROLE_SUPER_ADMINISTRATORR = "SUPER_ADMINISTRATOR";
  public static final String ROLE_SUPER_COMPLIANCE_OFFICER = "SUPER_COMPLIANCE_OFFICER";

  private SymphonyUserAttributes userAttributes;
  private SymphonyUserSystemAttributes userSystemInfo;
  private List<String> roles;
  private List<Long> features;
  private List<Long> apps;
  private List<Long> groups;
  private List<Long> disclaimers;
  private SymphonyUserAvatar avatar;
  private SymphonyPassword password;
}
