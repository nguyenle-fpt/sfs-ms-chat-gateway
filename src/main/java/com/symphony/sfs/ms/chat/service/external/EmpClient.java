package com.symphony.sfs.ms.chat.service.external;

import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.chat.model.FederatedAccount;

import java.util.List;
import java.util.Optional;

/**
 *
 */
public interface EmpClient {

  String CREATE_CHANNEL_ENDPOINT = "/api/v1/channel/create";
  String SEND_MESSAGE_ENDPOINT = "/api/v1/messages";

  Optional<String> createChannel(String emp, String streamId, List<FederatedAccount> federatedUsers, String initiatorUserId, List<IUser> symphonyUsers);

  /**
   * Currently only IM supported as we have only one recipient: toFederatedServiceAccount
   *
   * @param emp
   * @param streamId
   * @param messageId
   * @param fromSymphonyUser
   * @param toFederatedAccount
   * @param timestamp
   * @param message
   * @return
   */
  Optional<String> sendMessage(String emp, String streamId, String messageId, IUser fromSymphonyUser, FederatedAccount toFederatedAccount, Long timestamp, String message);
}
