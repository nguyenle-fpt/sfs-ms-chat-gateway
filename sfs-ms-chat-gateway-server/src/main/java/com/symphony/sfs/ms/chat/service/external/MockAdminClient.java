package com.symphony.sfs.ms.chat.service.external;

import com.symphony.sfs.ms.admin.generated.model.AdvisorUpdateRequest;
import com.symphony.sfs.ms.admin.generated.model.BlockedFileTypes;
import com.symphony.sfs.ms.admin.generated.model.CanChatResponse;
import com.symphony.sfs.ms.admin.generated.model.EmpList;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Optional;

@Setter
@Getter
public class MockAdminClient implements AdminClient {

  private Optional<CanChatResponse> canChatResponse = Optional.empty();

  @Override
  public EmpList getEmpList() {
    return new EmpList();
  }

  @Override
  public Optional<CanChatResponse> canChat(String advisorSymphonyId, String federatedUserId, String entitlementType) {
    return canChatResponse;
  }

  @Override
  public void notifyLeaveRoom(String streamId, String requester, List<String> leavers) {
  }

  @Override
  public void updateAdvisorInfo(AdvisorUpdateRequest advisorUpdateRequest) {
  }

  @Override
  public Optional<BlockedFileTypes> getBlockedFileTypes(String streamId, String emp) {
    return null;
  }
}
