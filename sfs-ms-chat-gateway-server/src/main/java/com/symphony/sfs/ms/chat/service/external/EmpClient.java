package com.symphony.sfs.ms.chat.service.external;

import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageRequest;

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
  public default Optional<String> sendMessage(String emp, String streamId, String messageId, IUser fromSymphonyUser, FederatedAccount toFederatedAccount, Long timestamp, String message, String disclaimer) {
    return sendMessage(emp, streamId, messageId, fromSymphonyUser, Collections.singletonList(toFederatedAccount), timestamp, message, disclaimer);
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
  Optional<String> sendMessage(String emp, String streamId, String messageId, IUser fromSymphonyUser, List<FederatedAccount> toFederatedAccounts, Long timestamp, String message, String disclaimer);

  /**
   * IM
   *
   * @param emp
   * @param streamId
   * @param timestamp
   * @param message
   * @param type
   * @return
   */
  Optional<String> sendSystemMessage(String emp, String streamId, Long timestamp, String message, SendSystemMessageRequest.TypeEnum type);

  /**
   * Internal usage for QA
   *
   * @param symphonyId
   * @param emailAddress
   */
  void deleteAccountOrFail(String emp, String symphonyId, String emailAddress);
}
