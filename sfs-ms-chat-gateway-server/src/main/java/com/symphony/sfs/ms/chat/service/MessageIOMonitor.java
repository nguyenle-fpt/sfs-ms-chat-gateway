package com.symphony.sfs.ms.chat.service;

import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.starter.health.MeterManager;
import io.micrometer.core.instrument.Counter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class MessageIOMonitor {
  private final MeterManager meterManager;

  public MessageIOMonitor(MeterManager meterManager) {
    this.meterManager = meterManager;
  }

  public void onSendMessageFromSymphony(IUser fromSymphonyUser, List<FederatedAccount> toAccounts, String streamId) {
    meterManager.register(Counter.builder("sfs.messages.sent.from.symphony")
      .tag("stream", streamId)
      .tag("fromUser", fromSymphonyUser.getId().toString())
      .tag("toUser", toAccounts.get(0).getSymphonyUserId()))
      .increment();
  }

  public void onMessageBlockFromSymphony(BlockingCauseFromSymphony blockingCause, String streamId) {
    meterManager.register(Counter.builder("sfs.messages.blocked.from.symphony")
      .tag("stream", streamId)
      .tag("cause", blockingCause.blockingCause))
      .increment();
  }

  public void onSendMessageToSymphony(String fromUserId, String streamId) {
    meterManager.register(Counter.builder("sfs.messages.sent.to.symphony")
      .tag("stream", streamId)
      .tag("fromUser", fromUserId))
      .increment();
  }

  public void onMessageBlockToSymphony(BlockingCauseToSymphony blockingCause, String streamId) {
    meterManager.register(Counter.builder("sfs.messages.blocked.to.symphony")
      .tag("stream", streamId)
      .tag("cause", blockingCause.blockingCause))
      .increment();
  }

  @AllArgsConstructor
  public enum BlockingCauseFromSymphony {
    NOT_ENOUGH_MEMBER("not enough member"),
    NO_FEDERATED_ACCOUNT("no federated account"),
    NO_ENTITLEMENT_ACCESS("no entitlement access"),
    NO_CONTACT("no contact"),
    TOO_MUCH_MEMBERS("too much members"),
    ATTACHMENTS("attachments"),
    SOCIAL_MESSAGE_MALFORMED("social message malformed"),
    UNSUPPORTED_MESSAGE_CONTENTS("unsupported message contents"),
    NO_GATEWAY_MANAGED_ACCOUNT("no gateway managed account"),
    UNMANAGED_ACCOUNT("unmanaged account"),
    DECRYPTION_FAILED("decryption failed");

    private String blockingCause;
  }

  @AllArgsConstructor
  public enum BlockingCauseToSymphony {
    FEDERATED_ACCOUNT_NOT_FOUND("federated account not found"),
    ADVISOR_NO_LONGER_AVAILABLE("advisor no longer available"),
    UNKNOWN_SENDER("unknown sender");

    private String blockingCause;
  }
}
