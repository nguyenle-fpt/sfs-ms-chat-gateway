package com.symphony.sfs.ms.chat.service.external;

import com.symphony.sfs.ms.admin.generated.model.EmpList;

public class MockAdminClient implements AdminClient {

  @Override
  public EmpList getEmpList() {
    return new EmpList();
  }
}
