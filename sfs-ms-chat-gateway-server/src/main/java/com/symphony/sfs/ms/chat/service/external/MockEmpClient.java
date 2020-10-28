package com.symphony.sfs.ms.chat.service.external;

import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.chat.generated.model.RoomMemberRequest;
import com.symphony.sfs.ms.chat.mapper.RoomMemberDtoMapper;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.emp.generated.model.DeleteChannelResponse;
import com.symphony.sfs.ms.emp.generated.model.DeleteChannelsResponse;
import com.symphony.sfs.ms.emp.generated.model.Attachment;
import com.symphony.sfs.ms.emp.generated.model.RoomMemberResponse;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageRequest;
import com.symphony.sfs.ms.starter.util.BulkRemovalStatus;
import lombok.Getter;
import org.eclipse.jetty.util.ConcurrentHashSet;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class MockEmpClient implements EmpClient {

  private Map<String, String> channels = new ConcurrentHashMap<>();
  private Map<String, String> messages = new ConcurrentHashMap<>();
  private Set<String> deletedFederatedAccounts = new ConcurrentHashSet<>();

  @Override
  public Optional<String> createChannel(String emp, String streamId, List<FederatedAccount> federatedUsers, String initiatorUserId, List<IUser> symphonyUsers) {
    String operationId = channels.get(emp + ":" + streamId);
    if (operationId == null) {
      operationId = UUID.randomUUID().toString();
      channels.putIfAbsent(emp + ":" + streamId, operationId);
    }
    return Optional.ofNullable(operationId);
  }

  @Override
  public Optional<String> sendMessage(String emp, String streamId, String messageId, IUser fromSymphonyUser, List<FederatedAccount> toFederatedAccounts, Long timestamp, String message, String disclaimer, List<Attachment> attachments) {
    // For now use the same mock implementation as channels
    String operationId = messages.get(emp + ":" + streamId + ":" + messageId + ":" + fromSymphonyUser.getId().toString());
    if (operationId == null) {
      operationId = UUID.randomUUID().toString();
      messages.putIfAbsent(emp + ":" + streamId + ":" + messageId + ":" + fromSymphonyUser.getId().toString(), operationId);
    }
    return Optional.ofNullable(operationId);
  }

  @Override
  public Optional<String> sendSystemMessage(String emp, String streamId, Long timestamp, String message, SendSystemMessageRequest.TypeEnum type) {
    // For now use the same mock implementation as channels
    String operationId = messages.get(emp + ":" + streamId + ":" + message + ":" + type);
    if (operationId == null) {
      operationId = UUID.randomUUID().toString();
      messages.putIfAbsent(emp + ":" + streamId + ":" + message + ":" + type, operationId);
    }
    return Optional.of(operationId);
  }

  @Override
  public void deleteAccountOrFail(String emp, String symphonyId, String email) {
    deletedFederatedAccounts.add(symphonyId);
  }

  @Override
  public Optional<DeleteChannelsResponse> deleteChannels(List<String> streamIds, String emp) {
    DeleteChannelsResponse response = new DeleteChannelsResponse();
    streamIds.forEach(streamId -> {
      //simulate failure
      if (streamId.contains("failure")) {
        response.addReportItem(new DeleteChannelResponse().streamId(streamId).status(BulkRemovalStatus.FAILURE));
      } else if (this.channels.remove(emp + ":" + streamId) == null) {
        response.addReportItem(new DeleteChannelResponse().streamId(streamId).status(BulkRemovalStatus.NOT_FOUND));
      } else {
        response.addReportItem(new DeleteChannelResponse().streamId(streamId).status(BulkRemovalStatus.SUCCESS));
      }
    });
    return Optional.of(response);
  }

  @Override
  public Optional<RoomMemberResponse> addRoomMember(String streamId, String emp, com.symphony.sfs.ms.emp.generated.model.RoomMemberRequest empRoomMemberRequest) {
    RoomMemberResponse empRoomMemberResponse = RoomMemberDtoMapper.MAPPER.empRoomMemberRequestToEmpRoomMemberResponse(empRoomMemberRequest);
    empRoomMemberResponse.setStreamId(streamId);
    empRoomMemberResponse.setEmp(emp);
    return Optional.of(empRoomMemberResponse);
  }
}
