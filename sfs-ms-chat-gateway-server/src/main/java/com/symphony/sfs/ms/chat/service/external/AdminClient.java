package com.symphony.sfs.ms.chat.service.external;

import com.symphony.sfs.ms.admin.generated.model.AdvisorUpdateRequest;
import com.symphony.sfs.ms.admin.generated.model.BlockedFileTypes;
import com.symphony.sfs.ms.admin.generated.model.CanChatResponse;
import com.symphony.sfs.ms.admin.generated.model.EmpList;

import java.util.List;
import java.util.Optional;

public interface AdminClient {

  EmpList getEmpList();

  Optional<CanChatResponse> canChat(String advisorSymphonyId, String federatedUserId, String entitlementType);

   void notifyLeaveRoom(String streamId, String requester, List<String> leavers);

   void updateAdvisorInfo(AdvisorUpdateRequest advisorUpdateRequest);

   Optional<BlockedFileTypes> getBlockedFileTypes(String streamId, String tenantId, String emp);
}
