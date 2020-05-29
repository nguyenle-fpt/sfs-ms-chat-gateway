package com.symphony.sfs.ms.chat.service.external;

import com.symphony.sfs.ms.admin.generated.model.AdvisorResponse;
import com.symphony.sfs.ms.admin.generated.model.EmpList;

import java.util.Optional;

public class MockAdminClient implements AdminClient {

  @Override
  public EmpList getEmpList() {
    return new EmpList();
  }

  @Override
  public Optional<AdvisorResponse> getAdvisorAccess(String symphonyId, String emp) {
    return Optional.empty();
  }

}
