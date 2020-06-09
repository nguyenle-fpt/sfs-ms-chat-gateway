package com.symphony.sfs.ms.chat.service.external;

import com.symphony.sfs.ms.admin.generated.model.EmpList;
import com.symphony.sfs.ms.admin.generated.model.EntitlementResponse;

import java.util.Optional;

public class MockAdminClient implements AdminClient {

  @Override
  public EmpList getEmpList() {
    return new EmpList();
  }

  @Override
  public Optional<EntitlementResponse> getEntitlementAccess(String symphonyId, String entitlementType) {
    return Optional.empty();
  }

}
