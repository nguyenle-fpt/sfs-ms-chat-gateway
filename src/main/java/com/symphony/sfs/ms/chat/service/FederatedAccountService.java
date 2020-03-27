package com.symphony.sfs.ms.chat.service;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.datafeed.DatafeedListener;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.exception.FederatedAccountAlreadyExistsProblem;
import com.symphony.sfs.ms.chat.generated.model.CreateAccountRequest;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.symphony.AdminUserManagementService;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyUser;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyUserAttributes;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyUserKeyRequest;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.UserSession;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.symphony.sfs.ms.chat.service.symphony.SymphonyUser.ROLE_INDIVIDUAL;
import static com.symphony.sfs.ms.chat.service.symphony.SymphonyUserAttributes.ACCOUNT_TYPE_SYSTEM;
import static com.symphony.sfs.ms.chat.service.symphony.SymphonyUserKeyRequest.ACTION_SAVE;

@Slf4j
public class FederatedAccountService implements DatafeedListener {

  public static final String EMAIL_DOMAIN = "ces.symphony.com";

  private DatafeedSessionPool datafeedSessionPool;
  private FederatedAccountRepository federatedAccountRepository;
  private AdminUserManagementService adminUserManagementService;
  private AuthenticationService authenticationService;
  private PodConfiguration podConfiguration;
  private BotConfiguration botConfiguration;
  private ChatConfiguration chatConfiguration;

  public FederatedAccountService(DatafeedSessionPool datafeedSessionPool, FederatedAccountRepository federatedAccountRepository, AdminUserManagementService adminUserManagementService, AuthenticationService authenticationService, PodConfiguration podConfiguration, BotConfiguration botConfiguration, ChatConfiguration chatConfiguration) {
    this.datafeedSessionPool = datafeedSessionPool;
    this.federatedAccountRepository = federatedAccountRepository;
    this.adminUserManagementService = adminUserManagementService;
    this.authenticationService = authenticationService;
    this.podConfiguration = podConfiguration;
    this.botConfiguration = botConfiguration;
    this.chatConfiguration = chatConfiguration;
  }

  @Override
  public void onIMCreated(String streamId, List<Long> members, IUser initiator, boolean crosspod) {
    // TODO
  }

  public FederatedAccount createAccount(CreateAccountRequest request) {
    Optional<FederatedAccount> existingAccount = federatedAccountRepository.findByFederatedUserIdAndEmp(request.getFederatedUserId(), request.getEmp());
    if (existingAccount.isPresent()) {
      throw new FederatedAccountAlreadyExistsProblem();
    }

    try {
      SymphonyUser symphonyUser = createSymphonyUser(request.getFirstName(), request.getLastName(), request.getEmp());
      FederatedAccount federatedAccount = federatedAccountRepository.saveIfNotExists(newFederatedServiceAccount(request, symphonyUser));

      datafeedSessionPool.listenDatafeed(
        symphonyUser.getUserAttributes().getUserName(),
        symphonyUser.getUserSystemInfo().getId()
      );

      if (request.getAdvisors() != null && !request.getAdvisors().isEmpty()) {
        // TODO
      }

      return federatedAccount;
    } catch (ConditionalCheckFailedException e) {
      LOG.debug("Failed to save the federated account repository", e);
      throw new FederatedAccountAlreadyExistsProblem();
    }
  }

  public SymphonyUser createSymphonyUser(String firstName, String lastName, String emp) {
    UserSession botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());

    SymphonyUserKeyRequest userKey = SymphonyUserKeyRequest.builder()
      .key(chatConfiguration.getSharedPublicKey().getData())
      .action(ACTION_SAVE)
      .build();

    String uuid = UUID.randomUUID().toString();

    SymphonyUserAttributes userAttributes = SymphonyUserAttributes.builder()
      .displayName(displayName(firstName, lastName, emp))
      .userName(userName(emp, uuid))
      .emailAddress(emailAddress(emp, uuid))
      .currentKey(userKey)
      .accountType(ACCOUNT_TYPE_SYSTEM)
      .build();

    SymphonyUser user = SymphonyUser.builder()
      .userAttributes(userAttributes)
      .roles(Arrays.asList(ROLE_INDIVIDUAL))
      .build();

    return adminUserManagementService.createUser(user, podConfiguration.getUrl(), botSession);
  }

  private String displayName(String firstName, String lastName, String emp) {
    return firstName + ' ' + lastName + " [" + emp + "]";
  }

  /**
   * Returns a username with pattern: {emp}_{random string}
   *
   * @param emp
   * @param uuid
   * @return
   */
  private String userName(String emp, String uuid) {
    return emp + '_' + uuid;
  }

  /**
   * Returns an email address with pattern: {emp}_{random string}@{EMAIL_DOMAIN}
   *
   * @param emp
   * @param uuid
   * @return
   */
  private String emailAddress(String emp, String uuid) {
    return emp + '_' + uuid + '@' + EMAIL_DOMAIN;
  }

  private FederatedAccount newFederatedServiceAccount(CreateAccountRequest account, SymphonyUser symphonyUser) {

    FederatedAccount federatedServiceAccount = new FederatedAccount();

    federatedServiceAccount.setEmailAddress(account.getEmailAddress());
    federatedServiceAccount.setPhoneNumber(account.getPhoneNumber());
    federatedServiceAccount.setFirstName(account.getFirstName());
    federatedServiceAccount.setLastName(account.getLastName());
    federatedServiceAccount.setFederatedUserId(account.getFederatedUserId());

    federatedServiceAccount.setSymphonyUserId(String.valueOf(symphonyUser.getUserSystemInfo().getId()));
    federatedServiceAccount.setSymphonyUsername(symphonyUser.getUserAttributes().getUserName());
    federatedServiceAccount.setEmp(account.getEmp());

    return federatedServiceAccount;
  }
}
