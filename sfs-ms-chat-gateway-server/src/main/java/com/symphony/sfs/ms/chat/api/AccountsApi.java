package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.chat.generated.model.CreateAccountRequest;
import com.symphony.sfs.ms.chat.generated.model.CreateAccountResponse;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.service.FederatedAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotBlank;

@Slf4j
@RestController
public class AccountsApi implements com.symphony.sfs.ms.chat.generated.api.AccountsApi {

  private FederatedAccountService federatedAccountService;

  public AccountsApi(FederatedAccountService federatedAccountService) {
    this.federatedAccountService = federatedAccountService;
  }

  @Override
  public ResponseEntity<CreateAccountResponse> createAccount(CreateAccountRequest request) {
    FederatedAccount account = federatedAccountService.createAccount(request);
    CreateAccountResponse response = new CreateAccountResponse()
      .symphonyUserId(account.getSymphonyUserId())
      .symphonyUsername(account.getSymphonyUsername());
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<Void> deleteFederatedAccount(String federatedUserId, String emp) {
    LOG.info("Remove account | federatedUser={} emp={}", federatedUserId, emp);
    federatedAccountService.deleteAccount(emp, federatedUserId);
    return ResponseEntity.ok().build();
  }
}
