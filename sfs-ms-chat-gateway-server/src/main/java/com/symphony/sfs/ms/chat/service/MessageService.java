package com.symphony.sfs.ms.chat.service;

import com.google.common.annotations.VisibleForTesting;
import com.symphony.oss.models.chat.canon.UserEntity;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.oss.models.chat.canon.facade.User;
import com.symphony.sfs.ms.chat.datafeed.DatafeedListener;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.exception.FederatedAccountNotFoundProblem;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.starter.symphony.auth.UserSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService implements DatafeedListener {

  private final EmpClient empClient;
  private final FederatedAccountRepository federatedAccountRepository;
  private final ForwarderQueueConsumer forwarderQueueConsumer;
  private final DatafeedSessionPool datafeedSessionPool;

  @PostConstruct
  @VisibleForTesting
  public void registerAsDatafeedListener() {
    forwarderQueueConsumer.registerDatafeedListener(this);
  }

  @Override
  public void onIMMessage(String streamId, String messageId, IUser fromSymphonyUser, List<String> members, Long timestamp, String message) {

    if (members.size() < 2) {
      LOG.warn("M(IM) with streamId {} and messageId {} has less than 2 members", streamId, messageId);
      return;
    }

    // Recipients correspond to all userIds which are not fromUserId
    String fromSymphonyUserId = fromSymphonyUser.getId().toString();
    List<String> toUserIds = members.stream().filter(id -> !id.equals(fromSymphonyUserId)).collect(Collectors.toList());

    Optional<FederatedAccount> fromFederatedAccount = federatedAccountRepository.findBySymphonyId(fromSymphonyUserId);
    boolean isMessageFromFederatedUser = fromFederatedAccount.isPresent();

    if (!isMessageFromFederatedUser) {
      // (M)IM from Symphony to EMP(s)
      // Reminder: channel is created on the fly if needed in EMPs

      // Get recipient FederatedServiceAccount(s)
      if (toUserIds.size() > 1) {
        LOG.info("More than one recipient {} --> We are in MIM", toUserIds);
      }

      handleFromSymphonyIMorMIM(streamId, messageId, fromSymphonyUser, toUserIds, timestamp, message);
    } else {
      // IM from WhatsApp to Symphony
      LOG.info("MessageService.onIMMessage: message from WhatsApp to Symphony - streamdId={} messageId={} fromFederatedUserId={}", streamId, messageId, fromSymphonyUserId);

      // TODO To be implemented with https://perzoinc.atlassian.net/browse/CES-318
    }
  }

  private void handleFromSymphonyIMorMIM(String streamId, String messageId, IUser fromSymphonyUser, List<String> toUserIds, Long timestamp, String message ) {
    MultiValueMap<String, FederatedAccount> federatedAccountsByEmp = new LinkedMultiValueMap<>();
    List<IUser> symphonyUsers = new ArrayList<>();

    for (String symphonyId : toUserIds) {
      federatedAccountRepository.findBySymphonyId(symphonyId).ifPresentOrElse(federatedAccount -> federatedAccountsByEmp.add(federatedAccount.getEmp(), federatedAccount), () -> symphonyUsers.add(newIUser(symphonyId)));
    }

    if (federatedAccountsByEmp.isEmpty()) {
      // No federated account found
      throw new FederatedAccountNotFoundProblem();
    }

    try {
      for (Map.Entry<String, List<FederatedAccount>> entry : federatedAccountsByEmp.entrySet()) {
        List<FederatedAccount> toFederatedAccountsForEmp = entry.getValue();
        List<UserSession> userSessions = new ArrayList<>(toFederatedAccountsForEmp.size());

        for (FederatedAccount toFederatedAccount : toFederatedAccountsForEmp) {
          // TODO The process stops at first UnknownDatafeedUserException
          //  Some EMPs may have been called, others may have not
          //  Do we check first all sessions are ok?
          userSessions.add(datafeedSessionPool.refreshSession(toFederatedAccount.getSymphonyUserId()));
        }

        // TODO Define the behavior in case of message not correctly sent to all EMPs
        //  need a recovery mechanism to re-send message in case of error only on some EMPs
        //
        // TODO Clarify the behavior when all MIM federated users have not joined the MIM
        //  Example: a WhatsApp user must join a WhatsGroup to discuss in the associated
        //  Proposition 1: block the chat (system message indicated that the chat is not possible until everyone has joined
        //  Proposition 2: allow chatting as soon as one federated has joined. In this case, what about the history of messages?
         empClient.sendMessage(entry.getKey(), streamId, messageId, fromSymphonyUser, entry.getValue(), timestamp, message);
      }
    } catch(UnknownDatafeedUserException e){
        // should never happen, as we have a FederatedAccount
        throw new IllegalStateException(e);
    }
  }

  private IUser newIUser(String symphonyId) {
    // TODO resolve firstName and lastName
    UserEntity.Builder builder = new UserEntity.Builder()
      .withId(Long.valueOf(symphonyId))
      .withFirstName(symphonyId + "_firstName")
      .withSurname(symphonyId + "_lastName");
    return new User(builder);

  }
}
