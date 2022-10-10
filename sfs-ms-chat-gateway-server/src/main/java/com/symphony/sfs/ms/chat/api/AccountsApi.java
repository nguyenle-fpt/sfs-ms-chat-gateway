package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.chat.generated.model.CreateAccountRequest;
import com.symphony.sfs.ms.chat.generated.model.CreateAccountResponse;
import com.symphony.sfs.ms.chat.generated.model.GetAccountResponse;
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

import static com.symphony.sfs.ms.starter.logging.LogUtils.obfuscatePhone;

@Slf4j
@RestController
public class AccountsApi implements com.symphony.sfs.ms.chat.generated.api.AccountsApi {

  private final FederatedAccountService federatedAccountService;

  public AccountsApi(FederatedAccountService federatedAccountService) {
    this.federatedAccountService = federatedAccountService;
  }

  @Override
  @ContinueSpan
  public ResponseEntity<CreateAccountResponse> createAccount(CreateAccountRequest request) {
    MDC.put("federatedUserId", request.getFederatedUserId());
    MDC.put("emp", request.getEmp());
    LOG.info("Create account | federatedUserId={} phoneNumber={} emp={}", request.getFederatedUserId(), obfuscatePhone(request.getPhoneNumber()), request.getEmp());
    FederatedAccount account = federatedAccountService.createAccount(request);
    CreateAccountResponse response = new CreateAccountResponse()
      .symphonyUserId(account.getSymphonyUserId())
      .symphonyUsername(account.getSymphonyUsername());
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<Void> deleteFederatedAccount(String federatedUserId, String emp, String tenantId, Boolean deleteEMPAccount) {
    LOG.info("Delete account | federatedUserId={} emp={} tenantId={} deleteEMPAccount={}", federatedUserId, emp, tenantId, deleteEMPAccount);
    federatedAccountService.deleteAccount(emp, federatedUserId, tenantId, deleteEMPAccount);
    return ResponseEntity.ok().build();
  }

  @Override
  @ContinueSpan
  public ResponseEntity<UpdateAccountResponse> updateFederatedAccount(String federatedUserId, String tenantId, UpdateAccountRequest body) {
    MDC.put("federatedUserId", federatedUserId);
    LOG.info("Update account | tenantId={}", tenantId);
    LOG.debug("Update account | federatedUserId={} tenantId={} firstName={}, lastName={}, companyName={}, preferredLanguage={}", federatedUserId, tenantId, body.getFirstName(), body.getLastName(), body.getCompanyName(), body.getPreferredLanguage());
    FederatedAccount federatedAccount = federatedAccountService.updateAccount(federatedUserId, tenantId, body.getFirstName(), body.getLastName(), body.getCompanyName(), body.getPreferredLanguage());
    return ResponseEntity.ok(UpdateAccountResponseDtoMapper.MAPPER.federatedAccountToUpdateAccountResponse(federatedAccount));
  }
  
  @Override
  public ResponseEntity<GetAccountResponse> getFederatedAccount(String federatedUserId, String emp) {
    FederatedAccount account = federatedAccountService.getFederatedAccount(federatedUserId, emp);
    GetAccountResponse response = new GetAccountResponse().symphonyUsername(account.getSymphonyUsername());
    return ResponseEntity.ok(response);
  }
}
