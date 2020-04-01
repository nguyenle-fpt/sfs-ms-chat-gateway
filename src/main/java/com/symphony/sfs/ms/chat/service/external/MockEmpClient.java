package com.symphony.sfs.ms.chat.service.external;

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
  public Optional<String> createChannel(String emp, String streamId, List<FederatedAccount> federatedUsers, String initiatorUserId, List<String> symphonyUserIds) {
    String leaseId = channels.get(emp + ":" + streamId);
    if (leaseId == null) {
      leaseId = UUID.randomUUID().toString();
      channels.putIfAbsent(emp + ":" + streamId, leaseId);
    }
    return Optional.of(leaseId);
  }
}
