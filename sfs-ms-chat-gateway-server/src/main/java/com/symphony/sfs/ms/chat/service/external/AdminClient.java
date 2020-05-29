package com.symphony.sfs.ms.chat.service.external;

import com.symphony.sfs.ms.admin.generated.model.AdvisorResponse;
import com.symphony.sfs.ms.admin.generated.model.EmpList;

import java.util.Optional;

public interface AdminClient {

  EmpList getEmpList();

  Optional<AdvisorResponse> getAdvisorAccess(String symphonyId, String emp);
}
