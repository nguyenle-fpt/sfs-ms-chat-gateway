package com.symphony.sfs.ms.chat.service;

import com.symphony.sfs.ms.chat.generated.model.ConnectionRequestCreationFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.ConnectionRequestNotFoundProblem;
import com.symphony.sfs.ms.chat.generated.model.ConnectionRequestResponse;
import com.symphony.sfs.ms.chat.mapper.ConnectionRequestMapper;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.exception.ConnectionRequestNotFoundException;
import com.symphony.sfs.ms.starter.security.StaticSessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.xpod.ConnectionRequest;
import com.symphony.sfs.ms.starter.symphony.xpod.ConnectionRequestStatus;
import com.symphony.sfs.ms.starter.symphony.xpod.ConnectionsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionRequestManager {

  private final ConnectionsService connectionsService;
  private final PodConfiguration podConfiguration;
  private final AuthenticationService authenticationService;
  private final BotConfiguration botConfiguration;

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


  public ConnectionRequestResponse sendConnectionRequestFromBot(String toSymphonyId) {
    SymphonySession botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());

    ConnectionRequest connectionRequest = connectionsService.sendConnectionRequestIfNotExists(podConfiguration.getUrl(), new StaticSessionSupplier<>(botSession), toSymphonyId).orElseThrow(ConnectionRequestCreationFailedProblem::new);

    return ConnectionRequestMapper.MAPPER.ConnectionRequestToConnectionRequestResponse(connectionRequest);
  }


  public ConnectionRequestResponse getConnectionRequestFromBotStatus(String symphonyId)  {
    SymphonySession botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());
    try {
      ConnectionRequest connectionRequest = connectionsService.getConnectionRequestStatus(podConfiguration.getUrl(), new StaticSessionSupplier<>(botSession), symphonyId).orElseThrow();

      return ConnectionRequestMapper.MAPPER.ConnectionRequestToConnectionRequestResponse(connectionRequest);
    } catch (ConnectionRequestNotFoundException cnfe) {
      LOG.info("Connection request not found | symphonyId={}", symphonyId);
      throw new ConnectionRequestNotFoundProblem();
    }
  }
}
