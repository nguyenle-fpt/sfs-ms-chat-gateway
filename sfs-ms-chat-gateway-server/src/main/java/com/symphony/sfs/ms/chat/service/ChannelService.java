package com.symphony.sfs.ms.chat.service;

import com.google.common.annotations.VisibleForTesting;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.chat.datafeed.DatafeedListener;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import com.symphony.sfs.ms.chat.generated.model.CannotRetrieveStreamIdProblem;
import com.symphony.sfs.ms.chat.generated.model.CreateChannelFailedProblem;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.symphony.auth.UserSession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelService implements DatafeedListener {

  private final StreamService streamService;
  private final SymphonyMessageService symphonyMessageService;
  private final PodConfiguration podConfiguration;
  private final EmpClient empClient;
  private final ForwarderQueueConsumer forwarderQueueConsumer;
  private final DatafeedSessionPool datafeedSessionPool;
  private final FederatedAccountRepository federatedAccountRepository;

  @PostConstruct
  @VisibleForTesting
  public void registerAsDatafeedListener() {
    forwarderQueueConsumer.registerDatafeedListener(this);
  }

  @Override
  public void onIMCreated(String streamId, List<String> members, IUser initiator, boolean crosspod) {
    // TODO check also advisors like in connection requests?

    if (members.size() == 2) {
      members.remove(initiator.getId().toString());
      if (members.size() == 1) {
        String toFederatedAccountId = members.get(0);
        Optional<FederatedAccount> toFederatedAccount = federatedAccountRepository.findBySymphonyId(toFederatedAccountId);
        if (toFederatedAccount.isPresent()) {
          createIMChannel(streamId, initiator, toFederatedAccount.get());
        } else {
          LOG.warn("onIMCreated towards a non-federated account {}", toFederatedAccountId);
        }
      } else {
        LOG.warn("Member list does not contain initiation: list={} initiator={}", members, initiator.getId());
      }
    } else {
      // TODO MIMs/rooms
      LOG.warn("Only IMs supported");
    }
  }

  public String createIMChannel(FederatedAccount fromFederatedAccount, IUser toSymphonyUser) {
    try {
      UserSession session = datafeedSessionPool.refreshSession(fromFederatedAccount.getSymphonyUserId());
      return createIMChannel(session, fromFederatedAccount, toSymphonyUser);
    } catch (UnknownDatafeedUserException e) {
      // should never happen, as we have a FederatedAccount
      throw new IllegalStateException(e);
    }
  }

  public String createIMChannel(UserSession session, FederatedAccount fromFederatedAccount, IUser toSymphonyUser) {
    String streamId = streamService.getIM(podConfiguration.getUrl(), toSymphonyUser.getId().toString(), session)
      .orElseThrow(CannotRetrieveStreamIdProblem::new);

    empClient.createChannel(fromFederatedAccount.getEmp(), streamId, Collections.singletonList(fromFederatedAccount), fromFederatedAccount.getSymphonyUserId(), Collections.singletonList(toSymphonyUser))
      .orElseThrow(CreateChannelFailedProblem::new);

    symphonyMessageService.sendInfoMessage(session, streamId, "Hello, I'm ready to discuss with you");

    return streamId;
  }

  public String createIMChannel(String streamId, IUser fromSymphonyUser, FederatedAccount toFederatedAccount) {

    try {
      UserSession session = datafeedSessionPool.refreshSession(toFederatedAccount.getSymphonyUserId());

      empClient.createChannel(toFederatedAccount.getEmp(), streamId, Collections.singletonList(toFederatedAccount), toFederatedAccount.getSymphonyUserId(), Collections.singletonList(fromSymphonyUser))
        .orElseThrow(CreateChannelFailedProblem::new);

      // TODO have a nice message template
      // TODO this should not be here, but in the whatsapp EMP
      streamService.sendMessage(podConfiguration.getUrl(), streamId, "<messageML>Hello, I will be ready as soon as I join the whatsapp group</messageML>", session);

      return streamId;
    } catch (UnknownDatafeedUserException e) {
      // should never happen, as we have a FederatedAccount
      throw new IllegalStateException(e);
    }
  }

}
