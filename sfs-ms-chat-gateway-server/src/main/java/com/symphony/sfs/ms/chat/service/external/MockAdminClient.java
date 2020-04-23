package com.symphony.sfs.ms.chat.service.external;

import com.symphony.sfs.ms.admin.generated.model.EmpList;
import com.symphony.sfs.ms.chat.exception.AdvisorNotFoundException;
import model.UserInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MockAdminClient implements AdminClient {
  private Map<String, UserInfo> advisors = new HashMap<>();

  @Override
  public Optional<UserInfo> getAdvisor(String userId) throws AdvisorNotFoundException {
    Optional<UserInfo> userInfo = Optional.ofNullable(advisors.get(userId));
    userInfo.orElseThrow(() -> new AdvisorNotFoundException(userId));
    return userInfo;
  }

  @Override
  public EmpList getEmpList() {
    return new EmpList();
  }

  public void mockAdvisor(UserInfo requester) {
    advisors.put(requester.getId().toString(), requester);
  }
}
