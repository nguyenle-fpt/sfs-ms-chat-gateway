package com.symphony.sfs.ms.chat.service.external;

import com.symphony.sfs.ms.chat.model.FederatedAccount;

import java.util.List;
import java.util.Optional;

/**
 *
 */
public interface EmpClient {

  String CREATE_CHANNEL_ENDPOINT = "/api/v1/channel/create";

  Optional<String> createChannel(String emp, String streamId, List<FederatedAccount> federatedUsers, String initiatorUserId, List<String> symphonyUserIds);
}
