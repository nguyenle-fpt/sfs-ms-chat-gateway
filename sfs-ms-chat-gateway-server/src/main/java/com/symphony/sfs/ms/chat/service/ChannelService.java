package com.symphony.sfs.ms.chat.service;

import com.google.common.annotations.VisibleForTesting;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.admin.generated.model.CanChatResponse;
import com.symphony.sfs.ms.chat.datafeed.DatafeedListener;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import com.symphony.sfs.ms.chat.generated.model.CannotRetrieveStreamIdProblem;
import com.symphony.sfs.ms.chat.generated.model.ChannelNotFoundProblem;
import com.symphony.sfs.ms.chat.generated.model.DeleteChannelRequest;
import com.symphony.sfs.ms.chat.model.Channel;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.ChannelRepository;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyService;
import com.symphony.sfs.ms.chat.util.SymphonyUserUtils;
import com.symphony.sfs.ms.emp.generated.model.DeleteChannelsResponse;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.security.StaticSessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.util.BulkRemovalStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelService implements DatafeedListener {

  private final StreamService streamService;
  private final SymphonyMessageSender symphonyMessageSender;
  private final PodConfiguration podConfiguration;
  private final EmpClient empClient;
  private final ForwarderQueueConsumer forwarderQueueConsumer;
  private final DatafeedSessionPool datafeedSessionPool;
  private final FederatedAccountRepository federatedAccountRepository;
  private final AdminClient adminClient;
  private final EmpSchemaService empSchemaService;
  private final SymphonyService symphonyService;
  private final ChannelRepository channelRepository;

  @PostConstruct
  @VisibleForTesting
  public void registerAsDatafeedListener() {
    forwarderQueueConsumer.registerDatafeedListener(this);
  }

  @Override
  @NewSpan
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

  @NewSpan
  public String createIMChannel(FederatedAccount fromFederatedAccount, IUser toSymphonyUser) {
    try {
      SymphonySession session = datafeedSessionPool.refreshSession(fromFederatedAccount.getSymphonyUserId());
      return createIMChannel(session, fromFederatedAccount, toSymphonyUser);
    } catch (UnknownDatafeedUserException e) {
      // should never happen, as we have a FederatedAccount
      throw new IllegalStateException(e);
    }
  }

  @NewSpan
  public String createIMChannel(SymphonySession session, FederatedAccount fromFederatedAccount, IUser toSymphonyUser) {
    String streamId = streamService.getIM(podConfiguration.getUrl(), new StaticSessionSupplier<>(session), toSymphonyUser.getId().toString())
      .orElseThrow(CannotRetrieveStreamIdProblem::new);
    Channel channel =  Channel.builder()
                              .streamId(streamId)
                              .advisorSymphonyId(toSymphonyUser.getId().toString())
                              .federatedUserId(fromFederatedAccount.getFederatedUserId())
                              .emp(fromFederatedAccount.getEmp())
                              .build();

    Optional<String> channelId = empClient.createChannel(fromFederatedAccount.getEmp(), streamId, Collections.singletonList(fromFederatedAccount), fromFederatedAccount.getSymphonyUserId(), Collections.singletonList(toSymphonyUser));
    if (channelId.isEmpty()) {
      symphonyMessageSender.sendAlertMessage(session, streamId, "Sorry, we are not able to open the discussion with your contact. Please contact your administrator.");
    } else {
      channelRepository.save(channel);
    }
    return streamId;
  }

  @NewSpan
  public String createIMChannel(String streamId, IUser fromSymphonyUser, FederatedAccount toFederatedAccount) {
    return createChannel(
      streamId,
      fromSymphonyUser,
      Collections.singletonMap(toFederatedAccount.getEmp(), Collections.singletonList(toFederatedAccount)),
      Collections.singletonList(fromSymphonyUser));
  }

  // CES-1292 - As part of the cleanup, I let this method as it is but put it in private. Some questions are still interesting when we decide to properly support MIM/Rooms
  private String createChannel(String streamId, IUser fromSymphonyUser, Map<String, List<FederatedAccount>> toFederatedAccounts, List<IUser> toSymphonyUsers) {

    try {
      for (Map.Entry<String, List<FederatedAccount>> entry : toFederatedAccounts.entrySet()) {
        List<FederatedAccount> toFederatedAccountsForEmp = entry.getValue();
        List<SymphonySession> userSessions = new ArrayList<>(toFederatedAccountsForEmp.size());
        for (FederatedAccount toFederatedAccount : toFederatedAccountsForEmp) {
          // TODO The process stops at first UnknownDatafeedUserException
          //  Some EMPs may have been called, others may have not
          //  Do we check first all sessions are ok?
          userSessions.add(datafeedSessionPool.refreshSession(toFederatedAccount.getSymphonyUserId()));
        }

        // Check there are only 2 users
        if (toFederatedAccounts.size() == 1 && toSymphonyUsers.size() == 1 && toSymphonyUsers.get(0) == fromSymphonyUser) {
          Optional<Channel>  existingChannel = channelRepository.findByAdvisorSymphonyIdAndFederatedUserIdAndEmp(fromSymphonyUser.getId().toString(), toFederatedAccountsForEmp.get(0).getFederatedUserId(), entry.getKey());
          if (existingChannel.isEmpty()) { // avoid recreating an already present channel
            Channel channel = Channel.builder()
              .advisorSymphonyId(fromSymphonyUser.getId().toString())
              .federatedUserId(toFederatedAccountsForEmp.get(0).getFederatedUserId())
              .emp(entry.getKey())
              .streamId(streamId)
              .build();
            // TODO need a recovery mechanism to re-trigger the failed channel creation
            //  Short term proposition: recovery is manual - display a System message in the MIM indicating that channel creation has failed for some EMPs and to contact an administrator
            Optional<String> channelId = empClient.createChannel(entry.getKey(), streamId, toFederatedAccountsForEmp, fromSymphonyUser.getId().toString(), toSymphonyUsers);
            if (channelId.isEmpty()) {
              userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, "Sorry, we are not able to open the discussion with your contact. Please contact your administrator."));
            } else {
              channelRepository.save(channel);
            }
          }
        } else {
          userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, "You are not allowed to invite a " + empSchemaService.getEmpDisplayName(entry.getKey()) + " contact in a MIM."));
        }
      }
    } catch (UnknownDatafeedUserException e) {
      // should never happen, as we have a FederatedAccount
      throw new IllegalStateException(e);
    }
    return streamId;
  }

  @Override
  @NewSpan
  public void onUserJoinedRoom(String streamId, List<String> members, IUser fromSymphonyUser) {
    MultiValueMap<String, FederatedAccount> federatedAccountsByEmp = new LinkedMultiValueMap<>();
    List<IUser> symphonyUsers = new ArrayList<>();

    for (String symphonyId : members) {
      federatedAccountRepository.findBySymphonyId(symphonyId).ifPresentOrElse(federatedAccount -> federatedAccountsByEmp.add(federatedAccount.getEmp(), federatedAccount), () -> symphonyUsers.add(SymphonyUserUtils.newIUser(symphonyId)));
    }

    refuseToJoinRoomOrMIM(streamId, federatedAccountsByEmp, true);
  }

  @NewSpan
  public void refuseToJoinRoomOrMIM(String streamId, Map<String, List<FederatedAccount>> toFederatedAccounts, boolean isRoom) {
    try {
      for (Map.Entry<String, List<FederatedAccount>> entry : toFederatedAccounts.entrySet()) {
        List<FederatedAccount> toFederatedAccountsForEmp = entry.getValue();
        List<SymphonySession> userSessions = new ArrayList<>(toFederatedAccountsForEmp.size());
        for (FederatedAccount toFederatedAccount : toFederatedAccountsForEmp) {
          // TODO The process stops at first UnknownDatafeedUserException
          //  Some EMPs may have been called, others may have not
          //  Do we check first all sessions are ok?
          userSessions.add(datafeedSessionPool.refreshSession(toFederatedAccount.getSymphonyUserId()));
        }

        // Remove all federated users
        if (isRoom) {
          // Send message to alert it is impossible to add WhatsApp user into a room
          userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, "You are not allowed to invite a " + empSchemaService.getEmpDisplayName(entry.getKey()) + " contact in a chat room."));
          userSessions.forEach(session -> symphonyService.removeMemberFromRoom(streamId, session));
        } else {
          // Send message to alert it is impossible to add WhatsApp user into a MIM
          userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, "You are not allowed to invite a " + empSchemaService.getEmpDisplayName(entry.getKey()) + " contact in a MIM."));
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
        Optional<CanChatResponse> canChatResponse = adminClient.canChat(initiator.getId().toString(), toFederatedAccount.get().getFederatedUserId(), toFederatedAccount.get().getEmp());
        if (canChatResponse.isPresent() && canChatResponse.get() == CanChatResponse.CAN_CHAT) {
          createIMChannel(streamId, initiator, toFederatedAccount.get());
        } else {
          // send error message
          try {
            String createChannelErrorMessage = null;
            if(canChatResponse.isPresent() && canChatResponse.get() == CanChatResponse.NO_ENTITLEMENT) {
              createChannelErrorMessage = "You are not entitled to send messages to " + empSchemaService.getEmpDisplayName(toFederatedAccount.get().getEmp()) + " users";
            } else {
              createChannelErrorMessage = "This message will not be delivered. You no longer have the entitlement for this.";
            }
            symphonyMessageSender.sendAlertMessage(datafeedSessionPool.refreshSession(toFederatedAccountId), streamId, createChannelErrorMessage);
          } catch (UnknownDatafeedUserException e) {
            throw new IllegalStateException();
          }
        }
      } else {
        LOG.warn("onIMCreated towards a non-federated account {}", toFederatedAccountId);
      }
    } else {
      LOG.warn("Member list does not contain initiator: list={} initiator={}", members, initiator.getId());
    }
  }

  private void handleMIMCreation(String streamId, List<String> members, IUser initiator, boolean crosspod) {
    MultiValueMap<String, FederatedAccount> federatedAccountsByEmp = new LinkedMultiValueMap<>();

    for (String symphonyId : members) {
      federatedAccountRepository.findBySymphonyId(symphonyId).ifPresent(federatedAccount -> federatedAccountsByEmp.add(federatedAccount.getEmp(), federatedAccount));
    }

    refuseToJoinRoomOrMIM(streamId, federatedAccountsByEmp, false);
  }


  @NewSpan
  public com.symphony.sfs.ms.chat.generated.model.DeleteChannelsResponse deleteChannels(List<DeleteChannelRequest> deleteChannelRequests) {
    Map<DeleteChannelRequest, BulkRemovalStatus> response = new HashMap<>();
    Map<String, Channel> channelsToDelete = new HashMap<>();

    // a map where the key is the emp and the value is the list of all requests related to it
    Map<String, List<DeleteChannelRequest>> channelsMap = deleteChannelRequests.stream()
      .collect(groupingBy(DeleteChannelRequest::getEntitlementType, mapping(Function.identity(), toList())));
    // a map to link each stream to the request related to it
    Map<String, DeleteChannelRequest> streamID2request = new HashMap<>();

    for (Map.Entry<String, List<DeleteChannelRequest>> currentEmp : channelsMap.entrySet()) {
      List<String> streamIDs = new ArrayList<>();
      // call to EMP to delete streams
      for (DeleteChannelRequest req : currentEmp.getValue()) {
        Optional<Channel> channel = channelRepository.findByAdvisorSymphonyIdAndFederatedUserIdAndEmp(req.getAdvisorSymphonyId(), req.getFederatedUserId(), req.getEntitlementType());
        if (channel.isEmpty()) {
          response.put(req, BulkRemovalStatus.NOT_FOUND);
        } else {
          streamID2request.put(channel.get().getStreamId(), req);
          streamIDs.add(channel.get().getStreamId());
          channelsToDelete.put(channel.get().getStreamId(), channel.get());
        }
      }
      Optional<DeleteChannelsResponse> result = empClient.deleteChannels(streamIDs, currentEmp.getKey());
      // delete for all streams failed
      if (result.isEmpty()) {
        currentEmp.getValue().forEach(channel -> response.put(channel, BulkRemovalStatus.FAILURE));
        // If the stream deletion failed, we will not attempt to remove the corresponding channels
        streamIDs.forEach(channelsToDelete::remove);
      } else {
        for (com.symphony.sfs.ms.emp.generated.model.DeleteChannelResponse deleteChannelResponse : result.get().getReport()) {
          DeleteChannelRequest request = streamID2request.get(deleteChannelResponse.getStreamId());
          if (BulkRemovalStatus.FAILURE.equals(deleteChannelResponse.getStatus())) {
            channelsToDelete.remove(deleteChannelResponse.getStreamId());
            response.put(request, BulkRemovalStatus.FAILURE);
          } else {
            // If the status is NOT_FOUND in EMP side, we still consider removal as a success because there are legitimate cases where that's possible
            response.put(request, BulkRemovalStatus.SUCCESS);
          }
        }
      }
    }

    // send messages for only streams for which the removal didn't fail in the EMP
    for (Channel channel : channelsToDelete.values()) {
      Optional<FederatedAccount> federatedAccount = federatedAccountRepository.findByFederatedUserIdAndEmp(channel.getFederatedUserId(), channel.getEmp());
      if (federatedAccount.isPresent()) {
        symphonyMessageSender.sendInfoMessage(channel.getStreamId(), federatedAccount.get().getSymphonyUserId(), "Your contact has been removed", channel.getAdvisorSymphonyId());
      }
    }
    //TODO deal with potential failure of batch delete and update status of corresponding channels accordingly (FAILURE)
    channelRepository.delete(channelsToDelete.values());
    return this.generateDeleteChannelsResponse(response);
  }

  private com.symphony.sfs.ms.chat.generated.model.DeleteChannelsResponse generateDeleteChannelsResponse(Map<DeleteChannelRequest, BulkRemovalStatus> response) {
    com.symphony.sfs.ms.chat.generated.model.DeleteChannelsResponse deleteChannelsResponse = new com.symphony.sfs.ms.chat.generated.model.DeleteChannelsResponse();
    deleteChannelsResponse.setReport(new ArrayList<>());
    for (Map.Entry<DeleteChannelRequest, BulkRemovalStatus> entry : response.entrySet()) {
      DeleteChannelRequest channel = entry.getKey();
      deleteChannelsResponse.addReportItem(
        new com.symphony.sfs.ms.chat.generated.model.DeleteChannelResponse().channel(new DeleteChannelRequest().advisorSymphonyId(channel.getAdvisorSymphonyId())
          .entitlementType(channel.getEntitlementType()).federatedUserId(channel.getFederatedUserId()))
          .status(entry.getValue()));
    }
    return deleteChannelsResponse;
  }

  @NewSpan
  public Channel retrieveChannelOrFail(String advisorSymphonyId, String federatedUserId, String emp) {
    return channelRepository.findByAdvisorSymphonyIdAndFederatedUserIdAndEmp(advisorSymphonyId, federatedUserId, emp).orElseThrow(ChannelNotFoundProblem::new);
  }

  @NewSpan
  public Optional<Channel> retrieveChannel(String advisorSymphonyId, String federatedUserId, String emp) {
    return channelRepository.findByAdvisorSymphonyIdAndFederatedUserIdAndEmp(advisorSymphonyId, federatedUserId, emp);
  }
}
