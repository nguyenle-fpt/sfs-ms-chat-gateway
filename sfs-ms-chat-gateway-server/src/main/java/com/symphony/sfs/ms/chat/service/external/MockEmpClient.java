package com.symphony.sfs.ms.chat.service.external;

import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.chat.mapper.RoomMemberDtoMapper;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.emp.generated.model.ChannelIdentifier;
import com.symphony.sfs.ms.emp.generated.model.DeleteChannelResponse;
import com.symphony.sfs.ms.emp.generated.model.DeleteChannelsResponse;
import com.symphony.sfs.ms.emp.generated.model.Attachment;
import com.symphony.sfs.ms.emp.generated.model.OperationIdBySymId;
import com.symphony.sfs.ms.emp.generated.model.RoomMemberResponse;
import com.symphony.sfs.ms.emp.generated.model.SendMessageResponse;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageRequest;
import com.symphony.sfs.ms.emp.generated.model.SendmessagerequestInlineMessage;
import com.symphony.sfs.ms.emp.generated.model.UpdateUserResponse;
import com.symphony.sfs.ms.starter.util.BulkRemovalStatus;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.jetty.util.ConcurrentHashSet;

import java.util.HashSet;
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
  @Getter
  @Setter
  private Set<String> federatedUserFailing = new HashSet<>();

  @Override
  public Optional<SendMessageResponse> sendMessage(String emp, String streamId, String messageId, IUser fromSymphonyUser, List<FederatedAccount> toFederatedAccounts, Long timestamp, String message, String disclaimer, List<Attachment> attachments, SendmessagerequestInlineMessage inlineMessage) {
    // For now use the same mock implementation as channels
    String operationId = messages.get(emp + ":" + streamId + ":" + messageId + ":" + fromSymphonyUser.getId().toString());
    if (operationId == null) {
      operationId = UUID.randomUUID().toString();
      messages.putIfAbsent(emp + ":" + streamId + ":" + messageId + ":" + fromSymphonyUser.getId().toString(), operationId);
    }
    return Optional.of(new SendMessageResponse().operationIds(List.of(new OperationIdBySymId().operationId(operationId))));
  }

  @Override
  public Optional<String> sendSystemMessage(String emp, String streamId, String fromSymphonyUserId, Long timestamp, String message, SendSystemMessageRequest.TypeEnum type) {
    // For now use the same mock implementation as channels
    String operationId = messages.get(emp + ":" + streamId + ":" + message + ":" + type);
    if (operationId == null) {
      operationId = UUID.randomUUID().toString();
      messages.putIfAbsent(emp + ":" + streamId + ":" + message + ":" + type, operationId);
    }
    return Optional.of(operationId);
  }

  @Override
  public void deleteAccountOrFail(String emp, String symphonyId, String phoneNumber, String tenantId) {
    deletedFederatedAccounts.add(symphonyId);
  }

  @Override
  public Optional<UpdateUserResponse> updateAccountOrFail(String emp, String symphonyId, String phoneNumber, String tenantId, String firstName, String lastName, String companyName) {
    return Optional.of(new UpdateUserResponse().firstName(firstName).lastName(lastName).companyName(companyName));
  }

  @Override
  public Optional<DeleteChannelsResponse> deleteChannels(List<ChannelIdentifier> channels, String emp) {
    DeleteChannelsResponse response = new DeleteChannelsResponse();
    channels.forEach(channel -> {
      //simulate failure
      if (channel.getStreamId().contains("failure")) {
        response.addReportItem(new DeleteChannelResponse().streamId(channel.getStreamId()).status(BulkRemovalStatus.FAILURE));
      } else if (this.channels.remove(emp + ":" + channel) == null) {
        response.addReportItem(new DeleteChannelResponse().streamId(channel.getStreamId()).status(BulkRemovalStatus.NOT_FOUND));
      } else {
        response.addReportItem(new DeleteChannelResponse().streamId(channel.getStreamId()).status(BulkRemovalStatus.SUCCESS));
      }
    });
    return Optional.of(response);
  }

  @Override
  public Optional<RoomMemberResponse> addRoomMemberOrFail(String streamId, String emp, com.symphony.sfs.ms.emp.generated.model.RoomMemberRequest empRoomMemberRequest) {
    if(federatedUserFailing.contains(empRoomMemberRequest.getFederatedUserId())) {
      return Optional.empty();
    }
    RoomMemberResponse empRoomMemberResponse = RoomMemberDtoMapper.MAPPER.empRoomMemberRequestToEmpRoomMemberResponse(empRoomMemberRequest);
    empRoomMemberResponse.setStreamId(streamId);
    empRoomMemberResponse.setEmp(emp);
    return Optional.of(empRoomMemberResponse);
  }
}
