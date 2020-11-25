package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.chat.generated.model.ConnectionRequestResponse;
import com.symphony.sfs.ms.chat.service.ConnectionRequestManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@Slf4j
@RestController
public class ConnectionRequestsApi implements com.symphony.sfs.ms.chat.generated.api.ConnectionRequestsApi {
  private final ConnectionRequestManager connectionRequestManager;

  public ConnectionRequestsApi(ConnectionRequestManager connectionRequestManager) {
    this.connectionRequestManager = connectionRequestManager;
  }


  @Override
  public ResponseEntity<ConnectionRequestResponse> sendConnectionRequest(@NotBlank @NotNull @Pattern(regexp = "^\\d+$") String symphonyId) {
    return ResponseEntity.ok(connectionRequestManager.sendConnectionRequestFromBot(symphonyId));
  }

  @Override
  public ResponseEntity<ConnectionRequestResponse> getConnectionRequestStatus(@NotBlank @NotNull @Pattern(regexp = "^\\d+$") String symphonyId) {
    return ResponseEntity.ok(connectionRequestManager.getConnectionRequestFromBotStatus(symphonyId));
  }
}
