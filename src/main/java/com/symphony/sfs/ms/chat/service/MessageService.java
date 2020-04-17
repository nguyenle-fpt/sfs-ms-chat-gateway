package com.symphony.sfs.ms.chat.service;

import com.google.common.annotations.VisibleForTesting;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.chat.datafeed.DatafeedListener;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.exception.FederatedAccountNotFoundProblem;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService implements DatafeedListener {

  private final EmpClient empClient;
  private final FederatedAccountRepository federatedAccountRepository;
  private final ForwarderQueueConsumer forwarderQueueConsumer;

  @PostConstruct
  @VisibleForTesting
  public void registerAsDatafeedListener() {
    forwarderQueueConsumer.registerDatafeedListener(this);
  }

  @Override
  public void onIMMessage(String streamId, String messageId, IUser fromSymphonyUser, List<String> members, Long timestamp, String message) {

    if (members.size() != 2) {
      return;
    }

    // Recipients correspond to all userIds which are not fromUserId
    String fromSymphonyUserId = fromSymphonyUser.getId().toString();
    List<String> toUserIds = members.stream().filter(id -> !id.equals(fromSymphonyUserId)).collect(Collectors.toList());

    Optional<FederatedAccount> fromFederatedAccount = federatedAccountRepository.findBySymphonyId(fromSymphonyUserId);
    boolean isMessageFromFederatedUser = fromFederatedAccount.isPresent();

    if (!isMessageFromFederatedUser) {
      // IM from Symphony to WhatsApp

      // Get recipient FederatedServiceAccount
      if (toUserIds.size() > 1) {
        LOG.warn("More than one recipient in IM: {}", toUserIds);
      }

      FederatedAccount toFederatedAccount = federatedAccountRepository.findBySymphonyId(toUserIds.get(0)).orElseThrow(FederatedAccountNotFoundProblem::new);

      empClient.sendMessage(toFederatedAccount.getEmp(), streamId, messageId, fromSymphonyUser, toFederatedAccount, timestamp, message);
    } else {
      // IM from WhatsApp to Symphony
      LOG.info("MessageService.onIMMessage: message from WhatsApp to Symphony - streamdId={} messageId={} fromFederatedUserId={}", streamId, messageId, fromSymphonyUserId);

      // TODO To be implemented with https://perzoinc.atlassian.net/browse/CES-318
    }
  }
}
