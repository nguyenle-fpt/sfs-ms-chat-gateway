package com.symphony.sfs.ms.chat.service.external;

import com.symphony.sfs.ms.admin.generated.model.EmpList;
import com.symphony.sfs.ms.chat.exception.AdvisorNotFoundException;
import model.UserInfo;

import java.util.Optional;

public interface AdminClient {

  String GET_ADVISOR_ENDPOINT = "/api/v1/internal/advisors/{userId}";

  /**
   * Retrieve, the advisor with the corresponding userId
   *
   * @param userId
   * @return
   */
  Optional<UserInfo> getAdvisor(String userId) throws AdvisorNotFoundException;

  EmpList getEmpList();
}
