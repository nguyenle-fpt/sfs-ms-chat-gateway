package com.symphony.sfs.ms.chat.service;

import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.security.StaticSessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.xpod.ConnectionRequest;
import com.symphony.sfs.ms.starter.symphony.xpod.ConnectionRequestStatus;
import com.symphony.sfs.ms.starter.symphony.xpod.ConnectionsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConnectionRequestManager {

  private final ConnectionsService connectionsService;
  private final PodConfiguration podConfiguration;

  public Optional<ConnectionRequestStatus> sendConnectionRequest(SymphonySession session, String toSymphonyId) {
    return connectionsService.sendConnectionRequestIfNotExists(podConfiguration.getUrl(), new StaticSessionSupplier<>(session), toSymphonyId)
      .map(ConnectionRequest::getStatus)
      .map(ConnectionRequestStatus::valueOf);
  }

  public Optional<ConnectionRequest> acceptConnectionRequest(SymphonySession session, String fromSymphonyId) {
    return connectionsService.acceptConnectionRequest(podConfiguration.getUrl(), new StaticSessionSupplier<>(session), fromSymphonyId);
  }

  public Optional<ConnectionRequest> refuseConnectionRequest(SymphonySession session, String fromSymphonyId) {
    return connectionsService.refuseConnectionRequest(podConfiguration.getUrl(), new StaticSessionSupplier<>(session), fromSymphonyId);
  }
}
