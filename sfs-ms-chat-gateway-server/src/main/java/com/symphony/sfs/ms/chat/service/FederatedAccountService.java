package com.symphony.sfs.ms.chat.service;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.Hashing;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.oss.models.chat.canon.facade.User;
import com.symphony.oss.models.core.canon.facade.PodAndUserId;
import com.symphony.sfs.ms.admin.generated.model.EmpEntity;
import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.datafeed.DatafeedListener;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool.DatafeedSession;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.generated.model.CreateAccountRequest;
import com.symphony.sfs.ms.chat.generated.model.CreateChannelRequest;
import com.symphony.sfs.ms.chat.generated.model.CreateUserFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.EmpNotFoundProblem;
import com.symphony.sfs.ms.chat.generated.model.FederatedAccountAlreadyExistsProblem;
import com.symphony.sfs.ms.chat.generated.model.FederatedAccountNotFoundProblem;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.symphony.AdminUserManagementService;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyUser;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyUserAttributes;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyUserKeyRequest;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.security.Session;
import com.symphony.sfs.ms.starter.security.SessionSupplier;
import com.symphony.sfs.ms.starter.security.StaticSessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.user.UsersInfoService;
import com.symphony.sfs.ms.starter.symphony.xpod.ConnectionRequestStatus;
import com.symphony.sfs.ms.starter.util.RsaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.UserInfo;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.symphony.sfs.ms.chat.service.symphony.SymphonyUser.ROLE_INDIVIDUAL;
import static com.symphony.sfs.ms.chat.service.symphony.SymphonyUserAttributes.ACCOUNT_TYPE_SYSTEM;
import static com.symphony.sfs.ms.chat.service.symphony.SymphonyUserKeyRequest.ACTION_SAVE;

@Slf4j
@Service
@RequiredArgsConstructor
public class FederatedAccountService implements DatafeedListener {

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
  private final UsersInfoService usersInfoService;
  private final EmpSchemaService empSchemaService;

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

    SymphonySession botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());

    try {
      SymphonyUser symphonyUser = createSymphonyUser(new StaticSessionSupplier<>(botSession), request.getFirstName(), request.getLastName(), request.getEmailAddress(), request.getEmp());
      FederatedAccount federatedAccount = federatedAccountRepository.saveIfNotExists(newFederatedServiceAccount(request, symphonyUser));

      datafeedSessionPool.listenDatafeed(federatedAccount);

      return federatedAccount;
    } catch (ConditionalCheckFailedException e) {
      LOG.debug("Failed to save the federated account repository, already exists", e);
      throw new FederatedAccountAlreadyExistsProblem();
    }
  }

  public String createChannel(CreateChannelRequest request) {
    Optional<FederatedAccount> federatedAccount = federatedAccountRepository.findByFederatedUserIdAndEmp(request.getFederatedUserId(), request.getEmp());
    if (federatedAccount.isEmpty()) {
      throw new FederatedAccountNotFoundProblem();
    }

    String advisorSymphonyId = request.getAdvisorUserId();
    SymphonySession botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());
    DatafeedSession session = datafeedSessionPool.listenDatafeed(federatedAccount.get());

    // If for whatever reason the connection request is already accepted
    // Maybe in case of offboarding and re-onboarding?
    //
    // Otherwise, the createIMChannel will be called when the ConnectionRequestStatus.ACCEPTED event is received from the forwarder queue
    if (connectionRequestManager.sendConnectionRequest(session, advisorSymphonyId).orElse(null) == ConnectionRequestStatus.ACCEPTED) {
      return channelService.createIMChannel(session, federatedAccount.get(), getCustomerInfo(advisorSymphonyId, botSession));
    }

    return null;
  }

  @Override
  public void onConnectionAccepted(IUser requesting, IUser requested) {
    federatedAccountRepository.findBySymphonyId(requesting.getId().toString())
      .ifPresent(account -> {
        channelService.createIMChannel(account, requested);
      });
  }

  public SymphonyUser createSymphonyUser(SessionSupplier<SymphonySession> session, String firstName, String lastName, String emailAddress, String emp) {
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
      .emailAddress(emailAddress(emailAddress, emp, uuid, session))
      .currentKey(userKey)
      .accountType(ACCOUNT_TYPE_SYSTEM)
      .build();

    SymphonyUser user = SymphonyUser.builder()
      .userAttributes(userAttributes)
      .roles(Arrays.asList(ROLE_INDIVIDUAL))
      .build();

    return adminUserManagementService.createUser(podConfiguration.getUrl(), session, user)
      .orElseThrow(CreateUserFailedProblem::new);
  }

  private String displayName(String firstName, String lastName, String empName) {
    EmpEntity emp = empSchemaService.getEmpDefinition(empName).orElseThrow(EmpNotFoundProblem::new);
    return firstName + ' ' + lastName + " [" + emp.getServiceAccountSuffix() + "]";
  }

  /**
   * Returns a username with pattern: {emp}_{random uuid}
   */
  private String userName(String emp, String uuid) {
    return emp + '_' + uuid;
  }

  /**
   * Returns an email address with pattern: {emp}_{random uuid}|{originalEmail}
   */
  private String emailAddress(String emailAddress, String emp, String uuid, SessionSupplier<SymphonySession> session) {
    List<String> candidates = IntStream.range(0, 5)
      .mapToObj(i -> Hashing.murmur3_32().hashString(UUID.randomUUID().toString() + emailAddress, StandardCharsets.UTF_8).toString() + '|' + emailAddress)
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

    federatedServiceAccount.setEmailAddress(account.getEmailAddress());
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

  private IUser getCustomerInfo(String symphonyId, SymphonySession botSession) {
    UserInfo info = usersInfoService.getUserFromId(podConfiguration.getUrl(), new StaticSessionSupplier<>(botSession), symphonyId)
      .orElseThrow(() -> new IllegalStateException("Error retrieving customer info"));

    return new User.Builder()
      .withId(PodAndUserId.newBuilder().build(Long.parseLong(symphonyId)))
      .withUsername(info.getUsername())
      .withFirstName(info.getFirstName())
      .withSurname(info.getLastName())
      .withCompany(info.getCompany())
      .build();
  }

}
