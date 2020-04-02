package com.symphony.sfs.ms.chat.service;

import com.google.common.annotations.VisibleForTesting;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.datafeed.DatafeedListener;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.generated.model.CannotRetrieveStreamIdProblem;
import com.symphony.sfs.ms.chat.generated.model.CreateChannelFailedProblem;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
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
  private final AuthenticationService authenticationService;
  private final PodConfiguration podConfiguration;
  private final ChatConfiguration chatConfiguration;
  private final EmpClient empClient;
  private final ForwarderQueueConsumer forwarderQueueConsumer;

  @PostConstruct
  @VisibleForTesting
  public void registerAsDatafeedListener() {
    forwarderQueueConsumer.registerDatafeedListener(this);
  }

  @Override
  public void onIMCreated(String streamId, List<Long> members, IUser initiator, boolean crosspod) {
    // TODO
  }

  public String createIMChannel(FederatedAccount fromFederatedAccount, String toSymphonyId) {
    UserSession session = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), fromFederatedAccount.getSymphonyUsername(), chatConfiguration.getSharedPrivateKey().getData());
    return createIMChannel(session, fromFederatedAccount, toSymphonyId);
  }

  public String createIMChannel(UserSession session, FederatedAccount fromFederatedAccount, String toSymphonyId) {
    String streamId = streamService.getIM(podConfiguration.getUrl(), toSymphonyId, session)
      .orElseThrow(CannotRetrieveStreamIdProblem::new);

    empClient.createChannel(fromFederatedAccount.getEmp(), streamId, Collections.singletonList(fromFederatedAccount), fromFederatedAccount.getSymphonyUserId(), Collections.singletonList(toSymphonyId))
      .orElseThrow(CreateChannelFailedProblem::new);
    return streamId;
  }

}
