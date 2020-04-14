package com.symphony.sfs.ms.chat.service;

import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.symphony.auth.UserSession;
import com.symphony.sfs.ms.starter.symphony.xpod.ConnectionRequestStatus;
import com.symphony.sfs.ms.starter.symphony.xpod.ConnectionsService;
import lombok.RequiredArgsConstructor;
import model.InboundConnectionRequest;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConnectionRequestManager {

  private final ConnectionsService connectionsService;
  private final PodConfiguration podConfiguration;

  public Optional<ConnectionRequestStatus> sendConnectionRequest(UserSession session, String toSymphonyId) {
    return connectionsService.getConnectionRequestStatus(podConfiguration.getUrl(), toSymphonyId, session)
      .or(() -> connectionsService.sendConnectionRequest(podConfiguration.getUrl(), toSymphonyId, session))
      .map(InboundConnectionRequest::getStatus)
      .map(ConnectionRequestStatus::valueOf);
  }

  public Optional<InboundConnectionRequest> acceptConnectionRequest(UserSession session, String fromSymphonyId) {
    return connectionsService.acceptConnectionRequest(podConfiguration.getUrl(), fromSymphonyId, session);
  }

  public Optional<InboundConnectionRequest> refuseConnectionRequest(UserSession session, String fromSymphonyId) {
    return connectionsService.refuseConnectionRequest(podConfiguration.getUrl(), fromSymphonyId, session);
  }
}
