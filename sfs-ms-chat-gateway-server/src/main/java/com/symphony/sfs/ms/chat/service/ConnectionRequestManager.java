package com.symphony.sfs.ms.chat.service;

import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.generated.model.ConnectionRequestCreationFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.ConnectionRequestNotFoundProblem;
import com.symphony.sfs.ms.chat.generated.model.ConnectionRequestResponse;
import com.symphony.sfs.ms.chat.mapper.ConnectionRequestMapper;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.exception.ConnectionRequestNotFoundException;
import com.symphony.sfs.ms.starter.symphony.xpod.ConnectionRequest;
import com.symphony.sfs.ms.starter.symphony.xpod.ConnectionsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionRequestManager {

  private final ConnectionsService connectionsService;
  private final PodConfiguration podConfiguration;
  private final DatafeedSessionPool datafeedSessionPool;


  public ConnectionRequestResponse sendConnectionRequestFromBot(String toSymphonyId) {

    ConnectionRequest connectionRequest = connectionsService.sendConnectionRequestIfNotExists(podConfiguration.getUrl(), datafeedSessionPool.getBotSessionSupplier(), toSymphonyId).orElseThrow(ConnectionRequestCreationFailedProblem::new);

    return ConnectionRequestMapper.MAPPER.ConnectionRequestToConnectionRequestResponse(connectionRequest);
  }


  public ConnectionRequestResponse getConnectionRequestFromBotStatus(String symphonyId)  {
    try {
      ConnectionRequest connectionRequest = connectionsService.getConnectionRequestStatus(podConfiguration.getUrl(), datafeedSessionPool.getBotSessionSupplier(), symphonyId).orElseThrow();

      return ConnectionRequestMapper.MAPPER.ConnectionRequestToConnectionRequestResponse(connectionRequest);
    } catch (ConnectionRequestNotFoundException cnfe) {
      LOG.info("Connection request not found | symphonyId={}", symphonyId);
      throw new ConnectionRequestNotFoundProblem();
    }
  }
}
