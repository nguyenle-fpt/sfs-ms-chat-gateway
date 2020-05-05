package com.symphony.sfs.ms.chat.service.external;

import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Getter
public class MockEmpClient implements EmpClient {

  private Map<String, String> channels = new HashMap<>();
  private Map<String, String> messages = new HashMap<>();

  @Override
  public Optional<String> createChannel(String emp, String streamId, List<FederatedAccount> federatedUsers, String initiatorUserId, List<IUser> symphonyUsers) {
    String operationId = channels.get(emp + ":" + streamId);
    if (operationId == null) {
      operationId = UUID.randomUUID().toString();
      channels.putIfAbsent(emp + ":" + streamId, operationId);
    }
    return Optional.of(operationId);
  }

  @Override
  public Optional<String> sendMessage(String emp, String streamId, String messageId, IUser fromSymphonyUser, FederatedAccount toFederatedAccount, Long timestamp, String message) {
    return sendMessage(emp, streamId, messageId, fromSymphonyUser, Collections.singletonList(toFederatedAccount), timestamp, message);
  }

  @Override
  public Optional<String> sendMessage(String emp, String streamId, String messageId, IUser fromSymphonyUser, List<FederatedAccount> toFederatedAccounts, Long timestamp, String message) {
    // For now use the same mock implementation as channels
    String operationId = messages.get(emp + ":" + streamId + ":" + messageId + ":" + fromSymphonyUser.getId().toString());
    if (operationId == null) {
      operationId = UUID.randomUUID().toString();
      messages.putIfAbsent(emp + ":" + streamId + ":" + messageId + ":" + fromSymphonyUser.getId().toString(), operationId);
    }
    return Optional.of(operationId);
  }
}
