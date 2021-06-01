package com.symphony.sfs.ms.chat.service.external;

import com.symphony.sfs.ms.admin.generated.model.CanChatResponse;
import com.symphony.sfs.ms.admin.generated.model.EmpList;
import com.symphony.sfs.ms.admin.generated.model.EntitlementResponse;
import com.symphony.sfs.ms.admin.generated.model.ImCreatedNotification;
import com.symphony.sfs.ms.admin.generated.model.RoomMembersIdentifiersResponse;
import com.symphony.sfs.ms.admin.generated.model.RoomResponse;

import java.util.List;
import java.util.Optional;

public interface AdminClient {

  EmpList getEmpList();

  Optional<EntitlementResponse> getEntitlementAccess(String symphonyId, String entitlementType);

  Optional<CanChatResponse> canChat(String advisorSymphonyId, String federatedUserId, String entitlementType);

  Optional<RoomResponse> createIMRoom(ImCreatedNotification imRequest);

   void notifyLeaveRoom(String streamId, String requester, List<String> leavers);
}
