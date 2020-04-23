package com.symphony.sfs.ms.chat.service.symphony;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Object used in Symphony Admin User Management REST API
 * <p>
 * See https://developers.symphony.com/restapi/reference#user-attributes
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SymphonyUserAttributes {

  public static final String ACCOUNT_TYPE_NORMAL = "NORMAL";
  public static final String ACCOUNT_TYPE_SYSTEM = "SYSTEM";

  private String accountType;
  private String emailAddress;
  private String firstName;
  private String lastName;
  private String userName;
  private String displayName;
  private String companyName;
  private String department;
  private String division;
  private String title;
  private String workPhoneNumber;
  private String mobilePhoneNumber;
  private String smsNumber;
  private String location;
  private String jobFunction;
  private List<String> assetClasses;
  private List<String> industries;
  private SymphonyUserKeyRequest currentKey;
  private SymphonyUserKeyRequest previousKey;

}
