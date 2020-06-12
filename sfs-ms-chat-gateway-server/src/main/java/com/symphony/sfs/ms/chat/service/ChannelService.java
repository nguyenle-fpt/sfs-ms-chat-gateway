package com.symphony.sfs.ms.chat.service;

import com.google.common.annotations.VisibleForTesting;
import com.symphony.oss.models.chat.canon.UserEntity;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.oss.models.chat.canon.facade.User;
import com.symphony.sfs.ms.chat.datafeed.DatafeedListener;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import com.symphony.sfs.ms.chat.generated.model.CannotRetrieveStreamIdProblem;
import com.symphony.sfs.ms.chat.generated.model.CreateChannelFailedProblem;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.symphony.auth.UserSession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelService implements DatafeedListener {

  private final StreamService streamService;
  private final SymphonyMessageService symphonyMessageService;
  private final PodConfiguration podConfiguration;
  private final EmpClient empClient;
  private final ForwarderQueueConsumer forwarderQueueConsumer;
  private final DatafeedSessionPool datafeedSessionPool;
  private final FederatedAccountRepository federatedAccountRepository;
  private final AdminClient adminClient;
  private final SymphonyService symphonyService;

  @PostConstruct
  @VisibleForTesting
  public void registerAsDatafeedListener() {
    forwarderQueueConsumer.registerDatafeedListener(this);
  }

  @Override
  public void onIMCreated(String streamId, List<String> members, IUser initiator, boolean crosspod) {
    if (members.size() < 2) {
      LOG.warn("IM initiated by {} with streamId {} has less than 2 members (count={})", initiator.getId().toString(), streamId, members.size());
    }

    // TODO check also advisors like in connection requests?

    if (members.size() == 2) {
      handleIMCreation(streamId, members, initiator, crosspod);
    } else {
      handleMIMCreation(streamId, members, initiator, crosspod);
    }
  }

  public String createIMChannel(FederatedAccount fromFederatedAccount, IUser toSymphonyUser) {
    try {
      UserSession session = datafeedSessionPool.refreshSession(fromFederatedAccount.getSymphonyUserId());
      return createIMChannel(session, fromFederatedAccount, toSymphonyUser);
    } catch (UnknownDatafeedUserException e) {
      // should never happen, as we have a FederatedAccount
      throw new IllegalStateException(e);
    }
  }

  public String createIMChannel(UserSession session, FederatedAccount fromFederatedAccount, IUser toSymphonyUser) {
    String streamId = streamService.getIM(podConfiguration.getUrl(), toSymphonyUser.getId().toString(), session)
      .orElseThrow(CannotRetrieveStreamIdProblem::new);

    Optional<String> channelId = empClient.createChannel(fromFederatedAccount.getEmp(), streamId, Collections.singletonList(fromFederatedAccount), fromFederatedAccount.getSymphonyUserId(), Collections.singletonList(toSymphonyUser));

    if (channelId.isEmpty()) {
      symphonyMessageService.sendAlertMessage(session, streamId, "Sorry, we are not able to open the discussion with your contact. Please contact your administrator.");
    }
    return streamId;
  }

  public String createIMChannel(String streamId, IUser fromSymphonyUser, FederatedAccount toFederatedAccount) {
    return createMIMChannel(
      streamId,
      fromSymphonyUser,
      Collections.singletonMap(toFederatedAccount.getEmp(), Collections.singletonList(toFederatedAccount)),
      Collections.singletonList(fromSymphonyUser));
  }

  public String createMIMChannel(String streamId, IUser fromSymphonyUser, Map<String, List<FederatedAccount>> toFederatedAccounts, List<IUser> toSymphonyUsers) {

    try {
      for (Map.Entry<String, List<FederatedAccount>> entry : toFederatedAccounts.entrySet()) {
        List<FederatedAccount> toFederatedAccountsForEmp = entry.getValue();
        List<UserSession> userSessions = new ArrayList<>(toFederatedAccountsForEmp.size());
        for (FederatedAccount toFederatedAccount : toFederatedAccountsForEmp) {
          // TODO The process stops at first UnknownDatafeedUserException
          //  Some EMPs may have been called, others may have not
          //  Do we check first all sessions are ok?
          userSessions.add(datafeedSessionPool.refreshSession(toFederatedAccount.getSymphonyUserId()));
        }

        // Check there are only 2 users
        if (toFederatedAccounts.size() == 1 && toSymphonyUsers.size() == 1 && toSymphonyUsers.get(0) == fromSymphonyUser) {
          // TODO need a recovery mechanism to re-trigger the failed channel creation
          //  Short term proposition: recovery is manual - display a System message in the MIM indicating that channel creation has failed for some EMPs and to contact an administrator
          empClient.createChannel(entry.getKey(), streamId, toFederatedAccountsForEmp, fromSymphonyUser.getId().toString(), toSymphonyUsers)
            .orElseThrow(CreateChannelFailedProblem::new);

          userSessions.forEach(session -> symphonyMessageService.sendInfoMessage(session, streamId, "Hello, I will be ready as soon as I join the whatsapp group"));
        } else {
          userSessions.forEach(session -> symphonyMessageService.sendAlertMessage(session, streamId, "You are not allowed to invite a " + entry.getKey() + " contact in a MIM."));
        }
      }
    } catch (UnknownDatafeedUserException e) {
      // should never happen, as we have a FederatedAccount
      throw new IllegalStateException(e);
    }
    return streamId;
  }

  public void deleteChannelByFederatedUserSymphonyIdAndEmp(String emp, String federatedUserId) {
    empClient.deleteChannelsBySymphonyId(emp, federatedUserId);
  }

  @Override
  public void onUserJoinedRoom(String streamId, List<String> members, IUser fromSymphonyUser) {
    MultiValueMap<String, FederatedAccount> federatedAccountsByEmp = new LinkedMultiValueMap<>();
    List<IUser> symphonyUsers = new ArrayList<>();

    for (String symphonyId : members) {
      federatedAccountRepository.findBySymphonyId(symphonyId).ifPresentOrElse(federatedAccount -> federatedAccountsByEmp.add(federatedAccount.getEmp(), federatedAccount), () -> symphonyUsers.add(newIUser(symphonyId)));
    }

    refuseToJoinRoomOrMIM(streamId, federatedAccountsByEmp, true);
  }

  public void refuseToJoinRoomOrMIM(String streamId, Map<String, List<FederatedAccount>> toFederatedAccounts, boolean isRoom) {
    try {
      for (Map.Entry<String, List<FederatedAccount>> entry : toFederatedAccounts.entrySet()) {
        List<FederatedAccount> toFederatedAccountsForEmp = entry.getValue();
        List<UserSession> userSessions = new ArrayList<>(toFederatedAccountsForEmp.size());
        for (FederatedAccount toFederatedAccount : toFederatedAccountsForEmp) {
          // TODO The process stops at first UnknownDatafeedUserException
          //  Some EMPs may have been called, others may have not
          //  Do we check first all sessions are ok?
          userSessions.add(datafeedSessionPool.refreshSession(toFederatedAccount.getSymphonyUserId()));
        }

        // Remove all federated users
        if (isRoom) {
          // Send message to alert it is impossible to add WhatsApp user into a room
          userSessions.forEach(session -> symphonyMessageService.sendAlertMessage(session, streamId, "You are not allowed to invite a " + entry.getKey() + " contact in a chat room."));

          userSessions.forEach(session -> symphonyService.removeMemberFromRoom(streamId, session));
        } else {
          // Send message to alert it is impossible to add WhatsApp user into a MIM
          userSessions.forEach(session -> symphonyMessageService.sendAlertMessage(session, streamId, "You are not allowed to invite a " + entry.getKey() + " contact in a MIM."));
        }
      }
    } catch (UnknownDatafeedUserException e) {
      // should never happen, as we have a FederatedAccount
      throw new IllegalStateException(e);
    }
  }

  private void handleIMCreation(String streamId, List<String> members, IUser initiator, boolean crosspod) {
    members.remove(initiator.getId().toString());
    if (members.size() == 1) {
      String toFederatedAccountId = members.get(0);
      //check canChat, we assume the initiator is not a federated user

      Optional<FederatedAccount> toFederatedAccount = federatedAccountRepository.findBySymphonyId(toFederatedAccountId);
      if (toFederatedAccount.isPresent()) {
        if (adminClient.getEntitlementAccess(initiator.getId().toString(), toFederatedAccount.get().getEmp()).isPresent()) {
          createIMChannel(streamId, initiator, toFederatedAccount.get());
        } else {
          // send error message
          try {
            symphonyMessageService.sendAlertMessage(datafeedSessionPool.refreshSession(toFederatedAccountId), streamId, "You are not entitled to send messages to " + toFederatedAccount.get().getEmp() + " users");
          } catch (UnknownDatafeedUserException e) {
            throw new IllegalStateException();
          }
        }
      } else {
        LOG.warn("onIMCreated towards a non-federated account {}", toFederatedAccountId);
      }
    } else {
      LOG.warn("Member list does not contain initiation: list={} initiator={}", members, initiator.getId());
    }
  }

  private void handleMIMCreation(String streamId, List<String> members, IUser initiator, boolean crosspod) {
    MultiValueMap<String, FederatedAccount> federatedAccountsByEmp = new LinkedMultiValueMap<>();
    List<IUser> symphonyUsers = new ArrayList<>();

    for (String symphonyId : members) {
      federatedAccountRepository.findBySymphonyId(symphonyId).ifPresentOrElse(federatedAccount -> federatedAccountsByEmp.add(federatedAccount.getEmp(), federatedAccount), () -> symphonyUsers.add(newIUser(symphonyId)));
    }

    refuseToJoinRoomOrMIM(streamId, federatedAccountsByEmp, false);

    // createMIMChannel(streamId, initiator, federatedAccountsByEmp, symphonyUsers);
  }

  private IUser newIUser(String symphonyId) {
    // TODO resolve firstName and lastName
    UserEntity.Builder builder = new UserEntity.Builder()
      .withId(Long.valueOf(symphonyId))
      .withFirstName(symphonyId + "_firstName")
      .withSurname(symphonyId + "_lastName");
    return new User(builder);

  }
}
