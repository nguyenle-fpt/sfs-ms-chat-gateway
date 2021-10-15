package com.symphony.sfs.ms.chat.service.external;

import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.emp.generated.model.ChannelIdentifier;
import com.symphony.sfs.ms.emp.generated.model.DeleteChannelsResponse;
import com.symphony.sfs.ms.emp.generated.model.Attachment;
import com.symphony.sfs.ms.emp.generated.model.RoomMemberResponse;
import com.symphony.sfs.ms.emp.generated.model.SendMessageResponse;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageRequest;
import com.symphony.sfs.ms.emp.generated.model.SendmessagerequestInlineMessage;
import com.symphony.sfs.ms.emp.generated.model.UpdateUserResponse;

import java.util.List;
import java.util.Optional;


public interface EmpClient {

  /**
   * MIM/Room with disclaimer
   *
   * @param emp
   * @param streamId
   * @param messageId
   * @param fromSymphonyUser
   * @param toFederatedAccounts
   * @param timestamp
   * @param message
   * @param disclaimer
   * @return
   */
  Optional<SendMessageResponse> sendMessage(String emp, String streamId, String messageId, IUser fromSymphonyUser, List<FederatedAccount> toFederatedAccounts, Long timestamp, String message, String disclaimer, List<Attachment> attachments, SendmessagerequestInlineMessage inlineMessage);

  /**
   * IM
   *
   * @param emp
   * @param streamId
   * @param symphonyId
   * @param timestamp
   * @param message
   * @param type
   * @return
   */
  Optional<String> sendSystemMessage(String emp, String streamId, String symphonyId, Long timestamp, String message, SendSystemMessageRequest.TypeEnum type);

  /**
   * Internal usage for QA
   *
   * @param symphonyId
   * @param phoneNumber
   * @param tenantId
   */
  void deleteAccountOrFail(String emp, String symphonyId, String phoneNumber, String tenantId);

  /**
   * EMP User
   * @param emp
   * @param symphonyId
   * @param phoneNumber
   * @param tenantId
   * @param firstName
   * @param lastName
   * @param companyName
   * @return
   */
  Optional<UpdateUserResponse> updateAccountOrFail(String emp, String symphonyId, String phoneNumber, String tenantId, String firstName, String lastName, String companyName);

  Optional<DeleteChannelsResponse> deleteChannels(List<ChannelIdentifier> deleteChannelRequests, String emp);

  /**
   *
   * @param streamId
   * @param emp
   * @param empRoomMemberRequest
   * @return
   */
  Optional<RoomMemberResponse> addRoomMemberOrFail(String streamId, String emp, com.symphony.sfs.ms.emp.generated.model.RoomMemberRequest empRoomMemberRequest);
}
