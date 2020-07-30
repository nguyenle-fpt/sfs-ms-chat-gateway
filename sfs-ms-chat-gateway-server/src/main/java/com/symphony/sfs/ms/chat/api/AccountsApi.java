package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.chat.generated.model.CreateAccountRequest;
import com.symphony.sfs.ms.chat.generated.model.CreateAccountResponse;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.service.FederatedAccountService;
import lombok.extern.slf4j.Slf4j;
import org.apache.log4j.MDC;
import org.springframework.cloud.sleuth.annotation.ContinueSpan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import static com.symphony.sfs.ms.starter.logging.LogUtils.obfuscatePhone;

@Slf4j
@RestController
public class AccountsApi implements com.symphony.sfs.ms.chat.generated.api.AccountsApi {

  private FederatedAccountService federatedAccountService;

  public AccountsApi(FederatedAccountService federatedAccountService) {
    this.federatedAccountService = federatedAccountService;
  }

  @Override
  @ContinueSpan
  public ResponseEntity<CreateAccountResponse> createAccount(CreateAccountRequest request) {
    MDC.put("federatedUserId", request.getFederatedUserId());
    MDC.put("emp", request.getEmp());
    LOG.info("create account | federatedUserId={} phoneNumber={} emp={}", request.getFederatedUserId(), obfuscatePhone(request.getPhoneNumber()), request.getEmp());
    FederatedAccount account = federatedAccountService.createAccount(request);
    CreateAccountResponse response = new CreateAccountResponse()
      .symphonyUserId(account.getSymphonyUserId())
      .symphonyUsername(account.getSymphonyUsername());
    return ResponseEntity.ok(response);
  }

  @Override
  @ContinueSpan
  public ResponseEntity<Void> deleteFederatedAccount(String federatedUserId, String emp) {
    MDC.put("federatedUserId", federatedUserId);
    MDC.put("emp", emp);
    LOG.info("delete account ");
    federatedAccountService.deleteAccount(emp, federatedUserId);
    return ResponseEntity.ok().build();
  }
}
