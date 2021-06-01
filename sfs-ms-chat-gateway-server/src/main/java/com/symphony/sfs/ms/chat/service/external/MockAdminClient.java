package com.symphony.sfs.ms.chat.service.external;

import com.symphony.sfs.ms.admin.generated.model.CanChatResponse;
import com.symphony.sfs.ms.admin.generated.model.EmpList;
import com.symphony.sfs.ms.admin.generated.model.EntitlementResponse;
import com.symphony.sfs.ms.admin.generated.model.ImCreatedNotification;
import com.symphony.sfs.ms.admin.generated.model.RoomMembersIdentifiersResponse;
import com.symphony.sfs.ms.admin.generated.model.RoomResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Setter
@Getter
public class MockAdminClient implements AdminClient {

  private Optional<CanChatResponse> canChatResponse = Optional.empty();
  private List<ImCreatedNotification> imRequests = new ArrayList<>();

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

  @Override
  public Optional<RoomResponse> createIMRoom(ImCreatedNotification imRequest) {
    imRequests.add(imRequest);
    return Optional.empty();
  }


  @Override
  public void notifyLeaveRoom(String streamId, String requester, List<String> leavers) {
  }
}
