package com.symphony.sfs.ms.chat.service.external;

import model.UserInfo;

import java.util.Optional;

/**
 * @author enrico.molino (10/04/2020)
 */
public class MockAdminClient implements AdminClient {
  @Override
  public Optional<UserInfo> getAdvisor(String userId) {
    return Optional.empty();
  }
}
