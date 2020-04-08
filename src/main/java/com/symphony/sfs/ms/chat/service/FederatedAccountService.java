package com.symphony.sfs.ms.chat.service;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.annotations.VisibleForTesting;
import com.symphony.oss.models.chat.canon.UserEntity;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.oss.models.chat.canon.facade.User;
import com.symphony.oss.models.core.canon.facade.PodAndUserId;
import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.datafeed.DatafeedListener;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool.DatafeedSession;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
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
import com.symphony.sfs.ms.starter.util.RsaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.AdminUserInfo;
import model.UserInfo;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.security.GeneralSecurityException;
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
  private final ForwarderQueueConsumer forwarderQueueConsumer;

  @PostConstruct
  @VisibleForTesting
  public void registerAsDatafeedListener() {
    forwarderQueueConsumer.registerDatafeedListener(this);
  }

  public FederatedAccount createAccount(CreateAccountRequest request) {
    Optional<FederatedAccount> existingAccount = federatedAccountRepository.findByFederatedUserIdAndEmp(request.getFederatedUserId(), request.getEmp());
    if (existingAccount.isPresent()) {
      throw new FederatedAccountAlreadyExistsProblem();
    }

    UserSession botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());

    try {
      SymphonyUser symphonyUser = createSymphonyUser(request.getFirstName(), request.getLastName(), request.getEmp(), botSession);
      FederatedAccount federatedAccount = federatedAccountRepository.saveIfNotExists(newFederatedServiceAccount(request, symphonyUser));

      DatafeedSession session = datafeedSessionPool.listenDatafeed(federatedAccount.getSymphonyUserId());

      if (request.getAdvisors() != null && !request.getAdvisors().isEmpty()) {
        String advisorSymphonyId = request.getAdvisors().get(0);
        if (connectionRequestManager.sendConnectionRequest(session, advisorSymphonyId).orElse(null) == ConnectionRequestStatus.ACCEPTED) {
          channelService.createIMChannel(session, federatedAccount, getCustomerInfo(advisorSymphonyId, botSession));
        }
      }

      return federatedAccount;
    } catch (ConditionalCheckFailedException e) {
      LOG.debug("Failed to save the federated account repository, already exists", e);
      throw new FederatedAccountAlreadyExistsProblem();
    } catch (UnknownDatafeedUserException e) {
      // should never happen, as we have a valid FederatedAccount
      throw new IllegalStateException(e);
    }
  }

  public void onConnectionAccepted(IUser requesting, IUser requested) {
    federatedAccountRepository.findBySymphonyId(requesting.getId().toString())
      .ifPresent(account -> {
        channelService.createIMChannel(account, requested);
      });
  }

  public SymphonyUser createSymphonyUser(String firstName, String lastName, String emp, UserSession botSession) {
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

  private IUser getCustomerInfo(String symphonyId, UserSession botSession) {
    AdminUserInfo info = adminUserManagementService.getUserInfo(symphonyId, podConfiguration.getUrl(), botSession).get(); // TODO better exception
    return new User.Builder()
      .withId(PodAndUserId.newBuilder().build(info.getUserSystemInfo().getId()))
      .withUsername(info.getUserAttributes().getUserName())
      .withFirstName(info.getUserAttributes().getFirstName())
      .withSurname(info.getUserAttributes().getLastName())
      .build();
  }

}
