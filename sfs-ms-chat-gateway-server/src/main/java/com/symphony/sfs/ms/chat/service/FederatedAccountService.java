package com.symphony.sfs.ms.chat.service;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.symphony.sfs.ms.admin.generated.model.EmpEntity;
import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.generated.model.CreateAccountRequest;
import com.symphony.sfs.ms.chat.generated.model.CreateUserFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.EmpNotFoundProblem;
import com.symphony.sfs.ms.chat.generated.model.FederatedAccountAlreadyExistsProblem;
import com.symphony.sfs.ms.chat.generated.model.FederatedAccountNotFoundProblem;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.ChannelRepository;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.security.SessionSupplier;
import com.symphony.sfs.ms.starter.security.StaticSessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonyAuthFactory;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.user.AdminUserManagementService;
import com.symphony.sfs.ms.starter.symphony.user.FeatureEntitlement;
import com.symphony.sfs.ms.starter.symphony.user.SymphonyUser;
import com.symphony.sfs.ms.starter.symphony.user.SymphonyUserAttributes;
import com.symphony.sfs.ms.starter.symphony.user.SymphonyUserKeyRequest;
import com.symphony.sfs.ms.starter.symphony.user.UserStatus;
import com.symphony.sfs.ms.starter.symphony.user.UsersInfoService;
import com.symphony.sfs.ms.starter.util.RsaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.UserInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.symphony.sfs.ms.starter.symphony.user.FeatureEntitlement.ENTITLEMENT_IS_EXTERNAL_ROOM_ENABLED;
import static com.symphony.sfs.ms.starter.symphony.user.SymphonyUser.ROLE_INDIVIDUAL;
import static com.symphony.sfs.ms.starter.symphony.user.SymphonyUserAttributes.ACCOUNT_TYPE_SYSTEM;
import static com.symphony.sfs.ms.starter.symphony.user.SymphonyUserKeyRequest.ACTION_SAVE;
import static com.symphony.sfs.ms.starter.util.FormatUtils.formatPhoneNumber;

@Slf4j
@Service
@RequiredArgsConstructor
public class FederatedAccountService {

  private final DatafeedSessionPool datafeedSessionPool;
  private final FederatedAccountRepository federatedAccountRepository;
  private final AdminUserManagementService adminUserManagementService;
  private final AuthenticationService authenticationService;
  private final PodConfiguration podConfiguration;
  private final BotConfiguration botConfiguration;
  private final ChatConfiguration chatConfiguration;
  private final UsersInfoService usersInfoService;
  private final EmpSchemaService empSchemaService;
  private final EmpClient empClient;
  private final SymphonyAuthFactory symphonyAuthFactory;
  private final ChannelRepository channelRepository;

  @NewSpan
  public FederatedAccount createAccount(CreateAccountRequest request) {
    Optional<FederatedAccount> existingAccount = federatedAccountRepository.findByFederatedUserIdAndEmp(request.getFederatedUserId(), request.getEmp());
    if (existingAccount.isPresent()) {
      throw new FederatedAccountAlreadyExistsProblem();
    }

    SymphonySession botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());

    try {
      SymphonyUser symphonyUser = createSymphonyUser(new StaticSessionSupplier<>(botSession), request.getFirstName(), request.getLastName(), request.getPhoneNumber(), request.getEmp());
      LOG.info("created symphony user | federatedUser={} symphonyId={}", request.getFederatedUserId(), symphonyUser.getUserSystemInfo().getId());
      FederatedAccount federatedAccount = federatedAccountRepository.saveIfNotExists(newFederatedServiceAccount(request, symphonyUser));

      datafeedSessionPool.listenDatafeed(federatedAccount);

      return federatedAccount;
    } catch (ConditionalCheckFailedException e) {
      LOG.debug("Failed to save the federated account repository, already exists", e);
      throw new FederatedAccountAlreadyExistsProblem();
    }
  }



  @NewSpan
  public void deleteAccount(String emp, String federatedUserId, String tenantId, boolean deleteEMPAccount) {
    LOG.info("Deleting account | federatedUserId={} emp={} deleteEMPAccount={}", federatedUserId, emp, deleteEMPAccount);
    FederatedAccount existingAccount = federatedAccountRepository.findByFederatedUserIdAndEmp(federatedUserId, emp)
      .orElseThrow(FederatedAccountNotFoundProblem::new);
    if (deleteEMPAccount) {
      empClient.deleteAccountOrFail(emp, existingAccount.getSymphonyUserId(), existingAccount.getPhoneNumber(), tenantId);
    }

    SymphonyUserAttributes attributes = new SymphonyUserAttributes();
    attributes.setDisplayName("DEACTIVATED");
    attributes.setUserName(UUID.randomUUID().toString() + "[DEACTIVATED]");
    attributes.setEmailAddress(UUID.randomUUID().toString() + "@deactivated.ces.symphony.com");
    adminUserManagementService.updateUser(podConfiguration.getUrl(), symphonyAuthFactory.getBotAuth(), existingAccount.getSymphonyUserId(), attributes);
    adminUserManagementService.updateUserStatus(podConfiguration.getUrl(), symphonyAuthFactory.getBotAuth(), existingAccount.getSymphonyUserId(), UserStatus.DISABLED);
    federatedAccountRepository.delete(existingAccount);
    channelRepository.findAllByFederatedUserId(federatedUserId).forEach(channelRepository::delete);

    datafeedSessionPool.removeSessionInMemory(existingAccount.getSymphonyUserId());
  }

  @NewSpan
  public FederatedAccount updateAccount(String federatedUserId, String tenantId, String firstName, String lastName, String companyName) {
    List<FederatedAccount> existingAccounts = federatedAccountRepository.findByFederatedUserId(federatedUserId);
    if (existingAccounts.size() == 0) {
      throw new FederatedAccountNotFoundProblem();
    }

    for(FederatedAccount federatedAccount : existingAccounts) {
      updateAccount(federatedAccount, tenantId, firstName, lastName, companyName);
    }

    return existingAccounts.get(0);
  }

  private void updateAccount(FederatedAccount federatedAccount, String tenantId, String firstName, String lastName, String companyName) {

    if (StringUtils.isNotBlank(firstName)) {
      federatedAccount.setFirstName(firstName);
    }
    if (StringUtils.isNotBlank(lastName)) {
      federatedAccount.setLastName(lastName);
    }
    if (StringUtils.isNotBlank(companyName)) {
      federatedAccount.setCompanyName(companyName);
    }

    // Check EMP against admin
    String userDisplayName = displayName(federatedAccount.getFirstName(), federatedAccount.getLastName(), federatedAccount.getEmp());
    SymphonyUserAttributes attributes = SymphonyUserAttributes.builder().displayName(userDisplayName).build();

    // All checks OK, update
    empClient.updateAccountOrFail(federatedAccount.getEmp(), federatedAccount.getSymphonyUserId(), federatedAccount.getPhoneNumber(), tenantId, firstName, lastName, companyName);
    adminUserManagementService.updateUser(podConfiguration.getUrl(), symphonyAuthFactory.getBotAuth(), federatedAccount.getSymphonyUserId(), attributes);


    federatedAccountRepository.save(federatedAccount);
  }

  @NewSpan
  public SymphonyUser createSymphonyUser(SessionSupplier<SymphonySession> session, String firstName, String lastName, String phoneNumber, String emp) {
    String publicKey = null;
    try {
      publicKey = RsaUtils.encodeRSAKey(RsaUtils.parseRSAPublicKey(chatConfiguration.getSharedPublicKey().getData()));
    } catch (GeneralSecurityException e) {
      LOG.error("Cannot parse shared public key");
    }

    SymphonyUserKeyRequest userKey = SymphonyUserKeyRequest.builder()
      .key(publicKey)
      .action(ACTION_SAVE)
      .build();

    SymphonyUserAttributes userAttributes = SymphonyUserAttributes.builder()
      .displayName(displayName(firstName, lastName, emp))
      .userName(userName(emp, phoneNumber))
      .emailAddress(emailAddress(session))
      .currentKey(userKey)
      .accountType(ACCOUNT_TYPE_SYSTEM)
      .build();

    SymphonyUser user = SymphonyUser.builder()
      .userAttributes(userAttributes)
      .roles(Arrays.asList(ROLE_INDIVIDUAL))
      .build();

    SymphonyUser symphonyUser = adminUserManagementService.createUser(podConfiguration.getUrl(), session, user)
      .orElseThrow(CreateUserFailedProblem::new);

    // CES-2442
    // Add isExternalRoomEnabled entitlement
    adminUserManagementService.updateUserFeatures(
      podConfiguration.getUrl(),
      session,
      String.valueOf(symphonyUser.getUserSystemInfo().getId()),
      Collections.singletonList(FeatureEntitlement.builder().entitlment(ENTITLEMENT_IS_EXTERNAL_ROOM_ENABLED).enabled(true).build()));
    return symphonyUser;
  }

  private String displayName(String firstName, String lastName, String empName) {
    EmpEntity emp = empSchemaService.getEmpDefinition(empName).orElseThrow(EmpNotFoundProblem::new);
    return firstName + ' ' + lastName + " [" + emp.getServiceAccountSuffix() + "]";
  }

  /**
   * Returns a username with pattern: {emp}_{phoneNumber}
   * a suffix is added for avoid conflicts with DES environment
   */
  private String userName(String emp, String phoneNumber) {
    return emp + '_' + formatPhoneNumber(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164) + ((StringUtils.isNotBlank(podConfiguration.getUsernameSuffix()))? podConfiguration.getUsernameSuffix() : "");
  }

  /**
   * Returns an email address with pattern: {uuid}@symphony.com
   */
  private String emailAddress(SessionSupplier<SymphonySession> session) {
    List<String> candidates = IntStream.range(0, 5)
      .mapToObj(i -> UUID.randomUUID().toString() + "@symphony.com")
      .collect(Collectors.toList());

    List<UserInfo> userInfos = usersInfoService.getUsersFromEmails(podConfiguration.getUrl(), session, candidates);
    userInfos.forEach(info -> candidates.remove(info.getEmailAddress()));

    if (candidates.isEmpty()) { // all emails are conflicting
      throw new IllegalStateException("Conflict while allocating email");
    }

    return candidates.get(0);
  }

  private FederatedAccount newFederatedServiceAccount(CreateAccountRequest account, SymphonyUser symphonyUser) {

    FederatedAccount federatedServiceAccount = new FederatedAccount();

    federatedServiceAccount.setPhoneNumber(account.getPhoneNumber());
    federatedServiceAccount.setFirstName(account.getFirstName());
    federatedServiceAccount.setLastName(account.getLastName());
    federatedServiceAccount.setCompanyName(account.getCompanyName());
    federatedServiceAccount.setFederatedUserId(account.getFederatedUserId());

    federatedServiceAccount.setSymphonyUserId(String.valueOf(symphonyUser.getUserSystemInfo().getId()));
    federatedServiceAccount.setSymphonyUsername(symphonyUser.getUserAttributes().getUserName());
    federatedServiceAccount.setEmp(account.getEmp());

    return federatedServiceAccount;
  }

}
