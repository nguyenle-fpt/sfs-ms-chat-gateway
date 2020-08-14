package com.symphony.sfs.ms.chat.service.external;

import com.symphony.sfs.ms.admin.generated.model.CanChatResponse;
import com.symphony.sfs.ms.admin.generated.model.EmpList;
import com.symphony.sfs.ms.admin.generated.model.EntitlementResponse;
import lombok.Setter;

import java.util.Optional;

@Setter
public class MockAdminClient implements AdminClient {

  private Optional<CanChatResponse> canChatResponse = Optional.empty();

  @Override
  public EmpList getEmpList() {
    return new EmpList();
  }

  @Override
  public Optional<EntitlementResponse> getEntitlementAccess(String symphonyId, String entitlementType) {
    return Optional.empty();
  }

  @Override
  public Optional<CanChatResponse> canChat(String advisorSymphonyId, String federatedUserId, String entitlementType) {
    return canChatResponse;
  }

}
