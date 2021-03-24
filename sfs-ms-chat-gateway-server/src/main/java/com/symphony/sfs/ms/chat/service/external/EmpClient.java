package com.symphony.sfs.ms.chat.service.external;

import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.emp.generated.model.ChannelIdentifier;
import com.symphony.sfs.ms.emp.generated.model.DeleteChannelsResponse;
import com.symphony.sfs.ms.emp.generated.model.Attachment;
import com.symphony.sfs.ms.emp.generated.model.OperationIdBySymId;
import com.symphony.sfs.ms.emp.generated.model.RoomMemberResponse;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageRequest;
import com.symphony.sfs.ms.emp.generated.model.UpdateUserResponse;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 *
 */
public interface EmpClient {

  String CREATE_CHANNEL_ENDPOINT = "/api/v1/channel/create";
  String SEND_MESSAGE_ENDPOINT = "/api/v1/messages";
  String SEND_SYSTEM_MESSAGE_ENDPOINT = "/api/v1/system-messages";

  // TODO MAKE IT THROW
  Optional<String> createChannel(String emp, String streamId, List<FederatedAccount> federatedUsers, String initiatorUserId, List<IUser> symphonyUsers);

  /**
   * IM
   *
   * @param emp
   * @param streamId
   * @param messageId
   * @param fromSymphonyUser
   * @param toFederatedAccount
   * @param timestamp
   * @param message
   * @param disclaimer
   * @return
   */
  public default Optional<List<OperationIdBySymId>> sendMessage(String emp, String streamId, String messageId, IUser fromSymphonyUser, FederatedAccount toFederatedAccount, Long timestamp, String message, String disclaimer, List<Attachment> attachments) {
    return sendMessage(emp, streamId, messageId, fromSymphonyUser, Collections.singletonList(toFederatedAccount), timestamp, message, disclaimer, attachments);
  }

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
  Optional<List<OperationIdBySymId>> sendMessage(String emp, String streamId, String messageId, IUser fromSymphonyUser, List<FederatedAccount> toFederatedAccounts, Long timestamp, String message, String disclaimer, List<Attachment> attachments);

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
   *
   * @param emp
   * @param channels
   * @param message
   * @return
   */
  void sendSystemMessageToChannels(String emp, List<ChannelIdentifier> channels, String message, boolean shouldBeResent);

  /**
   * Internal usage for QA
   *
   * @param symphonyId
   * @param emailAddress
   */
  void deleteAccountOrFail(String emp, String symphonyId, String emailAddress, String phoneNumber);

  /**
   * EMP User
   * @param emp
   * @param symphonyId
   * @param emailAddress
   * @param firstName
   * @param lastName
   * @param companyName
   * @return
   */
  Optional<UpdateUserResponse> updateAccountOrFail(String emp, String symphonyId, String emailAddress, String phoneNumber, String firstName, String lastName, String companyName);

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
