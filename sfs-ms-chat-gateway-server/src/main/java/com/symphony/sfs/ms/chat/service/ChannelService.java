package com.symphony.sfs.ms.chat.service;

import com.google.common.annotations.VisibleForTesting;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.chat.datafeed.DatafeedListener;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import com.symphony.sfs.ms.chat.generated.model.ChannelNotFoundProblem;
import com.symphony.sfs.ms.chat.generated.model.DeleteChannelRequest;
import com.symphony.sfs.ms.chat.model.Channel;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.ChannelRepository;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.emp.generated.model.ChannelIdentifier;
import com.symphony.sfs.ms.emp.generated.model.DeleteChannelsResponse;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.util.BulkRemovalStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelService implements DatafeedListener {

  private final SymphonyMessageSender symphonyMessageSender;
  private final EmpClient empClient;
  private final ForwarderQueueConsumer forwarderQueueConsumer;
  private final DatafeedSessionPool datafeedSessionPool;
  private final FederatedAccountRepository federatedAccountRepository;
  private final EmpSchemaService empSchemaService;
  private final ChannelRepository channelRepository;
  private final MessageSource messageSource;

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
      handleIMCreation(streamId, members, initiator);
    } else {
      handleMIMCreation(streamId, members);
    }
  }

  @Override
  @NewSpan
  public void onUserJoinedRoom(String streamId, List<String> members, IUser fromSymphonyUser) {

  }

  @NewSpan
  public void refuseToJoinMIM(String streamId, Map<String, List<FederatedAccount>> toFederatedAccounts) {
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
        // Send message to alert it is impossible to add EMP user into a MIM
        String alertMessage = messageSource.getMessage("create.mim.not.supported", new Object[]{empSchemaService.getEmpDisplayName(entry.getKey())}, Locale.getDefault());
        userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, alertMessage, Collections.emptyList()));
      }
    } catch (UnknownDatafeedUserException e) {
      // should never happen, as we have a FederatedAccount
      throw new IllegalStateException(e);
    }
  }

  private void handleIMCreation(String streamId, List<String> members, IUser initiator) {
    members.remove(initiator.getId().toString());
    if (members.size() == 1) {
      String toFederatedAccountId = members.get(0);
      //we assume the initiator is not a federated user

      try {
        String createChannelErrorMessage = messageSource.getMessage("cannot.create.im", null, Locale.getDefault());
        symphonyMessageSender.sendAlertMessage(datafeedSessionPool.refreshSession(toFederatedAccountId), streamId, createChannelErrorMessage, Collections.emptyList());
      } catch (UnknownDatafeedUserException e) {
        throw new IllegalStateException();
      }

    } else {
      LOG.warn("Member list does not contain initiator: list={} initiator={}", members, initiator.getId());
    }
  }

  private void handleMIMCreation(String streamId, List<String> members) {
    MultiValueMap<String, FederatedAccount> federatedAccountsByEmp = new LinkedMultiValueMap<>();

    for (String symphonyId : members) {
      federatedAccountRepository.findBySymphonyId(symphonyId).ifPresent(federatedAccount -> federatedAccountsByEmp.add(federatedAccount.getEmp(), federatedAccount));
    }

    refuseToJoinMIM(streamId, federatedAccountsByEmp);
  }


  @NewSpan
  public com.symphony.sfs.ms.chat.generated.model.DeleteChannelsResponse deleteChannels(List<DeleteChannelRequest> deleteChannelRequests) {
    Map<DeleteChannelRequest, BulkRemovalStatus> response = new HashMap<>();
    List<DeleteChannelRequest> channelsToNotify = new ArrayList<>();
    // a map where the key is the emp and the value is the list of all requests related to it
    Map<String, List<DeleteChannelRequest>> channelsMap = deleteChannelRequests.stream()
      .collect(groupingBy(DeleteChannelRequest::getEntitlementType, mapping(Function.identity(), toList())));
    // a map to link each stream to the request related to it
    Map<Pair<String, String>, DeleteChannelRequest> streamID2request = deleteChannelRequests.stream()
      .collect(toMap(r -> Pair.of(r.getStreamId(), r.getFederatedSymphonyId()), Function.identity(), (r1, r2) -> r1));


    for (Map.Entry<String, List<DeleteChannelRequest>> currentEmp : channelsMap.entrySet()) {
      // call to EMP to delete streams
      List<ChannelIdentifier> deleteChannelsRequest = currentEmp.getValue().stream()
        .map(d -> new ChannelIdentifier().streamId(d.getStreamId()).symphonyId(d.getFederatedSymphonyId()))
        .collect(toList());
      Optional<DeleteChannelsResponse> result = empClient.deleteChannels(deleteChannelsRequest, currentEmp.getKey());
      // delete for all streams failed
      if (result.isEmpty()) {
        currentEmp.getValue().forEach(channel -> response.put(channel, BulkRemovalStatus.FAILURE));
        // If the stream deletion failed, we will not attempt to remove the corresponding channels
      } else {
        for (com.symphony.sfs.ms.emp.generated.model.DeleteChannelResponse deleteChannelResponse : result.get().getReport()) {
          DeleteChannelRequest request = streamID2request.get(Pair.of(deleteChannelResponse.getStreamId(), deleteChannelResponse.getSymphonyId()));
          if (BulkRemovalStatus.FAILURE.equals(deleteChannelResponse.getStatus())) {
            response.put(request, BulkRemovalStatus.FAILURE);
          } else {
            // If the status is NOT_FOUND in EMP side, we still consider removal as a success because there are legitimate cases where that's possible
            response.put(request, BulkRemovalStatus.SUCCESS);
            channelsToNotify.add(request);
          }
        }
      }
    }

    // send msgs
    for (DeleteChannelRequest channel : channelsToNotify) {
      Optional<FederatedAccount> federatedAccount = federatedAccountRepository.findBySymphonyId(channel.getFederatedSymphonyId());
      if (federatedAccount.isPresent()) {
        // We are in an IM, the connect bot is not a member
        String infoMessage = messageSource.getMessage("channel.deleted", null, Locale.getDefault());
        symphonyMessageSender.sendInfoMessage(channel.getStreamId(), federatedAccount.get().getSymphonyUserId(), infoMessage, null);
      }
    }
    return this.generateDeleteChannelsResponse(response);
  }

  private com.symphony.sfs.ms.chat.generated.model.DeleteChannelsResponse generateDeleteChannelsResponse(Map<DeleteChannelRequest, BulkRemovalStatus> response) {
    com.symphony.sfs.ms.chat.generated.model.DeleteChannelsResponse deleteChannelsResponse = new com.symphony.sfs.ms.chat.generated.model.DeleteChannelsResponse();
    deleteChannelsResponse.setReport(new ArrayList<>());
    for (Map.Entry<DeleteChannelRequest, BulkRemovalStatus> entry : response.entrySet()) {
      DeleteChannelRequest channel = entry.getKey();
      deleteChannelsResponse.addReportItem(
        new com.symphony.sfs.ms.chat.generated.model.DeleteChannelResponse().channel(channel)
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
