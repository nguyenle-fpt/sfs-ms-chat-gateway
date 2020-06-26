package com.symphony.sfs.ms.chat.service.external;

import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageRequest;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class MockEmpClient implements EmpClient {

  private Map<String, String> channels = new ConcurrentHashMap<>();
  private Map<String, String> messages = new ConcurrentHashMap<>();

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
  public Optional<String> sendMessage(String emp, String streamId, String messageId, IUser fromSymphonyUser, List<FederatedAccount> toFederatedAccounts, Long timestamp, String message, String disclaimer) {
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
  public void deleteChannelsBySymphonyId(String emp, String federatedUserId) {
    // No implementation for now
  }
}
