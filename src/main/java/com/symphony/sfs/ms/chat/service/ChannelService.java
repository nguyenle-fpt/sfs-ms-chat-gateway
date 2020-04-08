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
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.symphony.auth.UserSession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChannelService implements DatafeedListener {

  private final StreamService streamService;
  private final PodConfiguration podConfiguration;
  private final EmpClient empClient;
  private final ForwarderQueueConsumer forwarderQueueConsumer;
  private final DatafeedSessionPool datafeedSessionPool;

  @PostConstruct
  @VisibleForTesting
  public void registerAsDatafeedListener() {
    forwarderQueueConsumer.registerDatafeedListener(this);
  }

  @Override
  public void onIMCreated(String streamId, List<String> members, IUser initiator, boolean crosspod) {
    // TODO
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

    // TODO have a nice message template
    streamService.sendMessage(podConfiguration.getUrl(), streamId, "<messageML>Hello, I'm ready to discuss with you</messageML>", session);

    return streamId;
  }

}
