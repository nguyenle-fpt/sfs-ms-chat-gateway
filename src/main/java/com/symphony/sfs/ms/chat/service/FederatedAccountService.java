package com.symphony.sfs.ms.chat.service;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.annotations.VisibleForTesting;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.datafeed.DatafeedListener;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool.DatafeedSession;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.generated.model.CreateAccountRequest;
import com.symphony.sfs.ms.chat.generated.model.CreateUserFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.FederatedAccountAlreadyExistsProblem;
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
import com.symphony.sfs.ms.starter.symphony.xpod.ConnectionRequestStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static com.symphony.sfs.ms.chat.service.symphony.SymphonyUser.ROLE_INDIVIDUAL;
import static com.symphony.sfs.ms.chat.service.symphony.SymphonyUserAttributes.ACCOUNT_TYPE_SYSTEM;
import static com.symphony.sfs.ms.chat.service.symphony.SymphonyUserKeyRequest.ACTION_SAVE;

@Slf4j
@Service
@RequiredArgsConstructor
public class FederatedAccountService implements DatafeedListener {

  public static final String EMAIL_DOMAIN = "ces.symphony.com";

  private final DatafeedSessionPool datafeedSessionPool;
  private final FederatedAccountRepository federatedAccountRepository;
  private final AdminUserManagementService adminUserManagementService;
  private final AuthenticationService authenticationService;
  private final PodConfiguration podConfiguration;
  private final BotConfiguration botConfiguration;
  private final ChatConfiguration chatConfiguration;
  private final ConnectionRequestManager connectionRequestManager;
  private final ChannelService channelService;

  @PostConstruct
  @VisibleForTesting
  public void registerAsDatafeedListener(ForwarderQueueConsumer forwarderQueueConsumer) {
    forwarderQueueConsumer.registerDatafeedListener(this);
  }

  public FederatedAccount createAccount(CreateAccountRequest request) {
    Optional<FederatedAccount> existingAccount = federatedAccountRepository.findByFederatedUserIdAndEmp(request.getFederatedUserId(), request.getEmp());
    if (existingAccount.isPresent()) {
      throw new FederatedAccountAlreadyExistsProblem();
    }

    try {
      SymphonyUser symphonyUser = createSymphonyUser(request.getFirstName(), request.getLastName(), request.getEmp());
      FederatedAccount federatedAccount = federatedAccountRepository.saveIfNotExists(newFederatedServiceAccount(request, symphonyUser));

      DatafeedSession session = datafeedSessionPool.listenDatafeed(
        symphonyUser.getUserAttributes().getUserName(),
        symphonyUser.getUserSystemInfo().getId()
      );

      if (request.getAdvisors() != null && !request.getAdvisors().isEmpty()) {
        String advisorSymphonyId = request.getAdvisors().get(0);
        if (connectionRequestManager.sendConnectionRequest(session, advisorSymphonyId).orElse(null) == ConnectionRequestStatus.ACCEPTED) {
          channelService.createIMChannel(session, federatedAccount, advisorSymphonyId);
        }
      }

      return federatedAccount;
    } catch (ConditionalCheckFailedException e) {
      LOG.debug("Failed to save the federated account repository", e);
      throw new FederatedAccountAlreadyExistsProblem();
    }
  }

  public void onConnectionAccepted(IUser requesting, IUser requested) {
    federatedAccountRepository.findBySymphonyUserId(requesting.getId().getUserId().toString())
      .ifPresent(account -> {
        channelService.createIMChannel(account, requested.getId().getUserId().toString());
      });
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

    return adminUserManagementService.createUser(user, podConfiguration.getUrl(), botSession)
      .orElseThrow(CreateUserFailedProblem::new);
  }

  private String displayName(String firstName, String lastName, String emp) {
    return firstName + ' ' + lastName + " [" + emp + "]";
  }

  /**
   * Returns a username with pattern: {emp}_{random uuid}
   */
  private String userName(String emp, String uuid) {
    return emp + '_' + uuid;
  }

  /**
   * Returns an email address with pattern: {emp}_{random uuid}@{EMAIL_DOMAIN}
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
