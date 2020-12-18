package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.chat.generated.model.CreateAccountRequest;
import com.symphony.sfs.ms.chat.generated.model.CreateAccountResponse;
import com.symphony.sfs.ms.chat.generated.model.UpdateAccountRequest;
import com.symphony.sfs.ms.chat.generated.model.UpdateAccountResponse;
import com.symphony.sfs.ms.chat.mapper.UpdateAccountResponseDtoMapper;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.service.FederatedAccountService;
import lombok.extern.slf4j.Slf4j;
import org.apache.log4j.MDC;
import org.springframework.cloud.sleuth.annotation.ContinueSpan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

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
/*
  @Override
  public ResponseEntity<Void> deleteFederatedAccounts(@Valid DeleteAccountsRequest body) {
    federatedAccountService.deleteAccounts(body.getAccounts());
    return ResponseEntity.ok().build();
  }
*/

  @Override
  public ResponseEntity<Void> deleteFederatedAccount(String federatedUserId, String emp, Boolean deleteEMPAccount) {
    federatedAccountService.deleteAccount(emp, federatedUserId, deleteEMPAccount);
    return ResponseEntity.ok().build();  }

  @Override
  @ContinueSpan
  public ResponseEntity<UpdateAccountResponse> updateFederatedAccount(String federatedUserId, String emp, UpdateAccountRequest body) {
    MDC.put("federatedUserId", federatedUserId);
    MDC.put("emp", emp);
    LOG.info("update account ");
    LOG.debug("update account | firstName={}, lastName={}, companyName={}", body.getFirstName(), body.getLastName(), body.getCompanyName());
    FederatedAccount federatedAccount = federatedAccountService.updateAccount(emp, federatedUserId, body.getFirstName(), body.getLastName(), body.getCompanyName());
    return ResponseEntity.ok(UpdateAccountResponseDtoMapper.MAPPER.federatedAccountToUpdateAccountResponse(federatedAccount));
  }
}
