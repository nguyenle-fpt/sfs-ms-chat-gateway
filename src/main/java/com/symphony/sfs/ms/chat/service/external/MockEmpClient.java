package com.symphony.sfs.ms.chat.service.external;

import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Getter
public class MockEmpClient implements EmpClient {

  private Map<String, String> channels = new HashMap<>();

  @Override
  public Optional<String> createChannel(String emp, String streamId, List<FederatedAccount> federatedUsers, String initiatorUserId, List<IUser> symphonyUsers) {
    String operationId = channels.get(emp + ":" + streamId);
    if (operationId == null) {
      operationId = UUID.randomUUID().toString();
      channels.putIfAbsent(emp + ":" + streamId, operationId);
    }
    return Optional.of(operationId);
  }
}
