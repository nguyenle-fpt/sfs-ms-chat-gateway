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
public class SymphonyUserInfo {

  private Long id;
  private String emailAddress;
  private String firstName;
  private String lastName;
  private String displayName;
  private String title;
  private String company;
  private String username;
  private String location;
  private String workPhoneNumber;
  private String mobilePhoneNumber;
  private String jobFunction;
  private String department;
  private String division;
  private List<SymphonyUserAvatar> avatars;

}
