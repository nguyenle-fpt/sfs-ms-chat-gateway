package com.symphony.sfs.ms.chat.service;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.Hashing;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.oss.models.chat.canon.facade.User;
import com.symphony.oss.models.core.canon.facade.PodAndUserId;
import com.symphony.sfs.ms.admin.generated.model.CanChatResponse;
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
import com.symphony.sfs.ms.chat.generated.model.UnknownFederatedAccountProblem;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.ChannelRepository;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
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
import com.symphony.sfs.ms.starter.symphony.xpod.ConnectionRequestStatus;
import com.symphony.sfs.ms.starter.util.RsaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.UserInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
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
  private final EmpClient empClient;
  private final SymphonyAuthFactory symphonyAuthFactory;
  private final AdminClient adminClient;
  private final ChannelRepository channelRepository;

  @PostConstruct
  @VisibleForTesting
  public void registerAsDatafeedListener() {
    forwarderQueueConsumer.registerDatafeedListener(this);
  }

  @NewSpan
  public FederatedAccount createAccount(CreateAccountRequest request) {
    Optional<FederatedAccount> existingAccount = federatedAccountRepository.findByFederatedUserIdAndEmp(request.getFederatedUserId(), request.getEmp());
    if (existingAccount.isPresent()) {
      throw new FederatedAccountAlreadyExistsProblem();
    }

    SymphonySession botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());

    try {
      SymphonyUser symphonyUser = createSymphonyUser(new StaticSessionSupplier<>(botSession), request.getFirstName(), request.getLastName(), request.getEmailAddress(), request.getEmp());
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
  public void deleteAccount(String emp, String federatedUserId) {
    FederatedAccount existingAccount = federatedAccountRepository.findByFederatedUserIdAndEmp(federatedUserId, emp)
      .orElseThrow(FederatedAccountNotFoundProblem::new);

    empClient.deleteAccountOrFail(emp, existingAccount.getSymphonyUserId(), existingAccount.getEmailAddress());


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
  public FederatedAccount updateAccount(String emp, String federatedUserId, String firstName, String lastName, String companyName) {
    FederatedAccount existingAccount = federatedAccountRepository.findByFederatedUserIdAndEmp(federatedUserId, emp)
      .orElseThrow(FederatedAccountNotFoundProblem::new);

    if (StringUtils.isNotBlank(firstName)) {
      existingAccount.setFirstName(firstName);
    }
    if (StringUtils.isNotBlank(lastName)) {
      existingAccount.setLastName(lastName);
    }
    if (StringUtils.isNotBlank(companyName)) {
      existingAccount.setCompanyName(companyName);
    }

    // Check EMP against admin
    String userDisplayName = displayName(existingAccount.getFirstName(), existingAccount.getLastName(), emp);
    SymphonyUserAttributes attributes = SymphonyUserAttributes.builder().displayName(userDisplayName).build();

    // All checks OK, update
    empClient.updateAccountOrFail(emp, existingAccount.getSymphonyUserId(), existingAccount.getEmailAddress(), firstName, lastName, companyName);
    adminUserManagementService.updateUser(podConfiguration.getUrl(), symphonyAuthFactory.getBotAuth(), existingAccount.getSymphonyUserId(), attributes);

    return federatedAccountRepository.save(existingAccount);
  }

  @NewSpan
  public String createChannel(CreateChannelRequest request) {
    FederatedAccount existingAccount = federatedAccountRepository.findByFederatedUserIdAndEmp(request.getFederatedUserId(), request.getEmp()).orElseThrow(UnknownFederatedAccountProblem::new);

    String advisorSymphonyId = request.getAdvisorUserId();
    SymphonySession botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());
    DatafeedSession session = datafeedSessionPool.listenDatafeed(existingAccount);

    // If for whatever reason the connection request is already accepted
    // Maybe in case of offboarding and re-onboarding?
    //
    // Otherwise, the createIMChannel will be called when the ConnectionRequestStatus.ACCEPTED event is received from the forwarder queue
    LOG.info("sending connection request | advisor={} federatedUser={}", request.getAdvisorUserId(), request.getFederatedUserId());
    if (connectionRequestManager.sendConnectionRequest(session, advisorSymphonyId).orElse(null) == ConnectionRequestStatus.ACCEPTED) {
      LOG.info("Connection request already accepted | advisor={} federatedUser={}", request.getAdvisorUserId(), request.getFederatedUserId());
      return channelService.createIMChannel(session, existingAccount, getCustomerInfo(advisorSymphonyId, botSession));
    }

    return null;
  }

  @Override
  @NewSpan
  public void onConnectionAccepted(IUser requesting, IUser requested) {
    LOG.info("Connection request accepted | requesting={} requested={}", requesting.getId(), requested.getId());
    Optional<FederatedAccount>  federatedAccount = federatedAccountRepository.findBySymphonyId(requesting.getId().toString());
    if(federatedAccount.isPresent()) {
      FederatedAccount account = federatedAccount.get();
      Optional<CanChatResponse> canChatResponse = adminClient.canChat(requested.getId().toString(), account.getFederatedUserId(), account.getEmp());
      if(canChatResponse.isPresent() && canChatResponse.get() == CanChatResponse.CAN_CHAT) {
        channelService.createIMChannel(account, requested);
      }
    }
  }

  @NewSpan
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

    // we also test if the unprefixed address is free, and we will use it if available
    candidates.add(0, emailAddress);

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
