package com.symphony.sfs.ms.chat.service.external;

import model.UserInfo;

import java.util.Optional;

/**
 * @author enrico.molino (09/04/2020)
 */
public interface AdminClient {

  String GET_ADVISOR_ENDPOINT = "/v1/advisors/{userId}";

  /**
   * Retrieve, the advisor with the corresponding userId
   *
   * @param userId
   * @return
   */
  Optional<UserInfo> getAdvisor(String userId);
}
