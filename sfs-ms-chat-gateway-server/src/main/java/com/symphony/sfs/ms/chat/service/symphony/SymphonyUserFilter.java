package com.symphony.sfs.ms.chat.service.symphony;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Object used in Symphony Search Users REST API
 * <p>
 * See https://developers.symphony.com/restapi/reference#search-users
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SymphonyUserFilter {

  private String title;
  private String location;
  private String company;

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getCompany() {
    return company;
  }

  public void setCompany(String company) {
    this.company = company;
  }
}
