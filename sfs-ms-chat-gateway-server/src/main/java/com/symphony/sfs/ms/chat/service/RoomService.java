package com.symphony.sfs.ms.chat.service;

import com.google.common.annotations.VisibleForTesting;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.admin.generated.model.RoomMemberIdentifier;
import com.symphony.sfs.ms.admin.generated.model.RoomMembersIdentifiersResponse;
import com.symphony.sfs.ms.chat.datafeed.DatafeedListener;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.generated.model.AddRoomMemberFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.CreateRoomFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.FederatedAccountNotFoundProblem;
import com.symphony.sfs.ms.chat.generated.model.ReactivateRoomNotImplementedProblem;
import com.symphony.sfs.ms.chat.generated.model.RoomMemberRemoveRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomMemberRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomMemberResponse;
import com.symphony.sfs.ms.chat.generated.model.RoomRemoveRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomResponse;
import com.symphony.sfs.ms.chat.generated.model.UnknownFederatedAccountProblem;
import com.symphony.sfs.ms.chat.generated.model.UpdateRoomActivityMemberRequest;
import com.symphony.sfs.ms.chat.generated.model.UpdateRoomActivityMemberResponse;
import com.symphony.sfs.ms.chat.generated.model.UpdateRoomActivityRequest;
import com.symphony.sfs.ms.chat.generated.model.UpdateRoomActivityResponse;
import com.symphony.sfs.ms.chat.mapper.RoomMemberDtoMapper;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.emp.generated.model.ChannelIdentifier;
import com.symphony.sfs.ms.emp.generated.model.DeleteChannelsResponse;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageRequest;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.security.StaticSessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyRoom;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyRoomAttributes;
import com.symphony.sfs.ms.starter.symphony.user.UsersInfoService;
import com.symphony.sfs.ms.starter.util.BulkRemovalStatus;
import com.symphony.sfs.ms.starter.webclient.WebCallException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.UserInfo;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.symphony.sfs.ms.starter.util.ProblemUtils.getProblemDetail;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomService implements DatafeedListener {

  private final FederatedAccountRepository federatedAccountRepository;

  private final PodConfiguration podConfiguration;
  private final BotConfiguration botConfiguration;

  private final ForwarderQueueConsumer forwarderQueueConsumer;
  private final StreamService streamService;
  private final AuthenticationService authenticationService;
  private final UsersInfoService usersInfoService;

  private final EmpClient empClient;
  private final AdminClient adminClient;

  private final MessageSource messageSource;

  @PostConstruct
  @VisibleForTesting
  public void registerAsDatafeedListener() {
    forwarderQueueConsumer.registerDatafeedListener(this);
  }

  // TODO
  //  - Override onRoomXXX methods if needed
  //  - Update room related implementation if needed (onUserJoinedRoom, refuseToJoinRoomOrMIM)

  @NewSpan
  public RoomResponse createRoom(RoomRequest roomRequest) {

    // Create Symphony Room

    SymphonySession botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());

    SymphonyRoomAttributes roomAttributes = SymphonyRoomAttributes.builder()
      .name(roomRequest.getRoomName())
      .description(roomRequest.getRoomName())
//      .keywords(Arrays.asList(SymphonyKeyword.builder().key("key1").value("value1").build()))
      .membersCanInvite(false)
      .discoverable(false) // FALSE mandatory for Cross Pod Room
      .isPublic(false) // FALSE mandatory for Cross Pod Room
      .readOnly(false) // FALSE mandatory for Cross Pod Room
      .copyProtected(false) // assume users want to be able to copy
      .crossPod(true) // Obviously we want a crossPod rooms between Client Pods and Federation Pod
      .viewHistory(false) // FALSE mandatory for Cross Pod Room
      .multiLateralRoom(false) // Only Federation Pod + Client Pod
      .build();

    SymphonyRoom symphonyRoom = streamService.createRoom(podConfiguration.getUrl(), new StaticSessionSupplier<>(botSession), roomAttributes).orElseThrow(CreateRoomFailedProblem::new);
    RoomResponse roomResponse = new RoomResponse();
    roomResponse.setStreamId(symphonyRoom.getRoomSystemInfo().getId());
    roomResponse.setRoomName(symphonyRoom.getRoomAttributes().getName());

    return roomResponse;

  }

  public UpdateRoomActivityResponse updateRoomActivity(String streamId, UpdateRoomActivityRequest updateRoomActivityRequest){

    if(updateRoomActivityRequest.isSetActive()){
      //Not permitted yet
      throw new ReactivateRoomNotImplementedProblem();
    }

    SymphonySession botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());
    Map<String, List<ChannelIdentifier>> channelsToDelete = updateRoomActivityRequest.getMembers().stream()
      .filter(UpdateRoomActivityMemberRequest::isFederatedUser)
      .collect(
        Collectors.groupingBy(UpdateRoomActivityMemberRequest::getEmp, Collectors.mapping(r -> new ChannelIdentifier().streamId(streamId).symphonyId(r.getSymphonyId()), Collectors.toList())));

    List<UpdateRoomActivityMemberResponse> memberResponses = new ArrayList<>();
    //Deactivate the room from Emp side
    for(Map.Entry<String, List<ChannelIdentifier>> deleteChannelsRequests : channelsToDelete.entrySet()) {
      //TODO CES-3557
      // Send System message to emp's users in the chat before deleting the channels
      try {
        Optional<DeleteChannelsResponse> deleteChannelResponse = empClient.deleteChannels(deleteChannelsRequests.getValue(), deleteChannelsRequests.getKey());
        deleteChannelResponse.ifPresent(deleteChannelsResponse -> memberResponses.addAll(
          deleteChannelsResponse.getReport().stream().map(d ->
            new UpdateRoomActivityMemberResponse()
              .emp(deleteChannelsRequests.getKey())
              .symphonyId(d.getSymphonyId())
              .federatedUser(true)
              .status(d.getStatus())
          ).collect(Collectors.toList()))
        );
      } catch (Exception ex) {
        LOG.error("Fail deactivate Room in Emp| streamId={} emp={}", streamId, deleteChannelsRequests.getKey(), ex);
        memberResponses.addAll(deleteChannelsRequests.getValue().stream().map(ChannelIdentifier::getSymphonyId).map(symphonyId ->
          new UpdateRoomActivityMemberResponse()
            .emp(deleteChannelsRequests.getKey())
            .symphonyId(symphonyId)
            .federatedUser(true)
            .status(BulkRemovalStatus.FAILURE)).collect(Collectors.toList())
        );
      }
    }

    //TODO CES-3557
    // Send System messages to Room Advisors when reactivation will be planned
    // This message is not needed for the moment cause the room content is not accessible anymore when the room is deactivated

    //Deactivate the room from Symphony side
    streamService.setRoomActive(podConfiguration.getUrl(), new StaticSessionSupplier<>(botSession), streamId, false);

    memberResponses.addAll(updateRoomActivityRequest.getMembers().stream().filter(r -> !r.isFederatedUser()).map(UpdateRoomActivityMemberRequest::getSymphonyId).map(symphonyId ->
      new UpdateRoomActivityMemberResponse()
        .symphonyId(symphonyId)
        .federatedUser(false)
        .status(BulkRemovalStatus.SUCCESS)).collect(Collectors.toList())
    );

    return new UpdateRoomActivityResponse().members(memberResponses);
  }

  public RoomMemberResponse addRoomMember(String streamId, RoomMemberRequest roomMemberRequest) {

    // Add member in SymphonyRoom before delegating to EMPs because EMPs could send messages in the room directly after the room member is added
    SymphonySession botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());



    RoomMemberResponse roomMemberResponse = RoomMemberDtoMapper.MAPPER.roomMemberRequestToRoomMemberResponse(roomMemberRequest);
    roomMemberResponse.setStreamId(streamId);



    // If member is already part of the room, we receive the following response from Symphony:
    // "Member already part of the room or Xpod request will be processed asynchronously"
    // It is not an error so we can ignore it

    streamService.addRoomMember(podConfiguration.getUrl(), new StaticSessionSupplier<>(botSession), streamId, roomMemberRequest.getSymphonyId()).orElseThrow(AddRoomMemberFailedProblem::new);

    if (roomMemberRequest.isFederatedUser()) {
      FederatedAccount federatedAccount = federatedAccountRepository.findBySymphonyId(roomMemberRequest.getSymphonyId()).orElseThrow(UnknownFederatedAccountProblem::new);
      roomMemberResponse.setFederatedUserId(federatedAccount.getFederatedUserId());
      roomMemberResponse.setEmp(federatedAccount.getEmp());
      roomMemberResponse.setEmailAddress(federatedAccount.getEmailAddress());
      roomMemberResponse.setPhoneNumber(federatedAccount.getPhoneNumber());
      UserInfo advisorInfo = getUserInfo(roomMemberRequest.getAdvisorSymphonyId(), botSession);
      com.symphony.sfs.ms.emp.generated.model.RoomMemberRequest empRoomMemberRequest = RoomMemberDtoMapper.MAPPER.toEmpRoomMemberRequest(roomMemberRequest, federatedAccount, advisorInfo);
      // TODO If EMP error maybe indicate that the user has been added on Symphony side but not EMP side?
      try {
        empClient.addRoomMemberOrFail(streamId, federatedAccount.getEmp(), empRoomMemberRequest);
      } catch (WebCallException wce) {
        String problemDetail = getProblemDetail(wce);

        // rollback if there is an error
        LOG.error("error creating channel in add room member | federatedSymphonyId={} streamId={} problem={}", federatedAccount.getSymphonyUserId(), streamId, problemDetail);
        streamService.removeRoomMember(podConfiguration.getUrl(), new StaticSessionSupplier<>(botSession), streamId, roomMemberRequest.getSymphonyId());
        // CES-3756 - set detail of new AddRoomMemberFailedProblem with the detail provided in the in the raised exception
        // Example:
        // If there is BusinessApi available, then sfs-ms-whatapp throws NoBusinessApiAvailableProblem with no detail
        // problemDetail which is set in AddRoomMembverFailedProblem is the NoBusinessApiAvailableProblem.problemType = https://symphony.com/problems/no.business.api.available
        throw new AddRoomMemberFailedProblem(problemDetail);
      }
    }

    return roomMemberResponse;
  }

  @NewSpan
  public void removeMember(String streamId, String symphonyId, String emp, Boolean federatedUser, Boolean removeChannel) {
    SymphonySession botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());
    // we only have to send the delete channel if we want to remove the channel for this member (remove member case, not offboarding case)
    // and only for federated users because advisors do not have channels in emps
    if(federatedUser && removeChannel) {
      empClient.deleteChannels(Collections.singletonList(new ChannelIdentifier().streamId(streamId).symphonyId(symphonyId)), emp);
      //TODO manage status
    }
    // TODO send messages?
    streamService.removeRoomMember(podConfiguration.getUrl(), new StaticSessionSupplier<>(botSession), streamId, symphonyId);
  }

  // This is an *internal only* endpoint
  // List of room member needs to be synchronized with actual list of federated users.
  // If it isn't, some channels might still be alive EMP side
  @NewSpan
  public void deleteRoom(String streamId, RoomRemoveRequest roomRemoveRequest) {
    SymphonySession botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());
    Map<String, List<ChannelIdentifier>> channelsToDelete = roomRemoveRequest.getMembers().stream()
      .filter(r -> r.isFederatedUser() && r.isRemoveChannel())
      .collect(
          Collectors.groupingBy(RoomMemberRemoveRequest::getEmp, Collectors.mapping(r -> new ChannelIdentifier().streamId(streamId).symphonyId(r.getSymphonyId()), Collectors.toList())));

    for(Map.Entry<String, List<ChannelIdentifier>> deleteChannelsRequests : channelsToDelete.entrySet()) {
      //TODO manage status
      empClient.deleteChannels(deleteChannelsRequests.getValue(), deleteChannelsRequests.getKey());
    }

    //TODO do we need to remove everyone from the room if it is deleted ?
    streamService.setRoomActive(podConfiguration.getUrl(), new StaticSessionSupplier<>(botSession), streamId, false);
  }

  private UserInfo getUserInfo(String symphonyId, SymphonySession session) {
    return usersInfoService.getUserFromId(podConfiguration.getUrl(), new StaticSessionSupplier<>(session), symphonyId)
      .orElseThrow(() -> new IllegalStateException("Error retrieving customer info"));
  }

  @Override
  public void onUserJoinedRoom(String streamId, List<String> joiners, IUser fromSymphonyUser) {
    notifyWholeEmpRoomMembersJoinOrLeaveEvent(streamId, joiners,true);
  }

  @Override
  public void onUserLeftRoom(String streamId, IUser requestor, List<IUser> leavingUsers) {

    notifyWholeEmpRoomMembersJoinOrLeaveEvent(streamId, leavingUsers.stream().map(iUser -> iUser.getId().toString()).collect(Collectors.toList()),false);

    if(botConfiguration.getSymphonyId().equals(String.valueOf(requestor.getId()))) {
      LOG.info("On user left room ignoring bot action | streamId={}", streamId);
      return;
    }
    adminClient.notifyLeaveRoom(
      streamId,
      requestor.getId().toString(),
      leavingUsers.stream().map(user -> user.getId().toString()).collect(Collectors.toList())
    );

  }

  @NewSpan
  public void sendRoomMembersListToEmpUser(String streamId, String symphonyId, boolean isUserJoining) {
    try {
      FederatedAccount federatedAccount = federatedAccountRepository.findBySymphonyId(symphonyId).orElseThrow(FederatedAccountNotFoundProblem::new);
      StringBuilder text = new StringBuilder();
      List<RoomMemberIdentifier> roomMemberIdentifiers = getRoomMembersIdentifiers(streamId);
      if (isUserJoining) {
        //Create Welcome Message
        SymphonySession botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());
        Optional<SymphonyRoom> optionalSymphonyRoom = streamService.roomInfo(podConfiguration.getUrl(), new StaticSessionSupplier<>(botSession), streamId);
        String roomName;
        if(optionalSymphonyRoom.isPresent()) {
          roomName = optionalSymphonyRoom.get().getRoomAttributes().getName();
        } else {
          roomName = "the conversation";
        }
        text.append(messageSource.getMessage("user.joined.room", new Object[]{roomName}, Locale.getDefault()));
      }
      text.append(generateJoinOrLeaveTextMessage(new ArrayList<>(), roomMemberIdentifiers, isUserJoining));
      Optional<String> sendSystemMessage = empClient.sendSystemMessage(federatedAccount.getEmp(), streamId, symphonyId, OffsetDateTime.now().toEpochSecond(), text.toString(), SendSystemMessageRequest.TypeEnum.INFO);
      if (sendSystemMessage.isEmpty()) {
        LOG.error("Fail to send room members list | streamId={} symphonyId={} emp={}", streamId, symphonyId, federatedAccount.getEmp());
      }
    } catch (FederatedAccountNotFoundProblem problem){
      LOG.error("Federated Account not found | symphonyId={}", symphonyId, problem);
    }
  }

  private void notifyWholeEmpRoomMembersJoinOrLeaveEvent(String streamId, List<String> symphonyIds, boolean isJoining){
    //Room is always active if we are here (join and leave blocked on deactivated room)
    List<RoomMemberIdentifier> roomMemberIdentifiers = getRoomMembersIdentifiers(streamId);
    if(roomMemberIdentifiers.isEmpty()) {
      return;
    }

    List<String> roomMembersSymphonyIds = roomMemberIdentifiers.stream().map(RoomMemberIdentifier::getSymphonyId).collect(Collectors.toList());
    List<RoomMemberIdentifier> affectedUsers = roomMemberIdentifiers.stream().filter(roomMemberIdentifier -> symphonyIds.contains(roomMemberIdentifier.getSymphonyId())).collect(Collectors.toList());

    //Adjust for asynchronous trouble
    if (isJoining) {
      for (String symphonyId : symphonyIds) {
        if(!roomMembersSymphonyIds.contains(symphonyId)) {
          RoomMemberIdentifier roomMemberIdentifierToAdd = getRoomMemberIdentifierFromSymphonyId(symphonyId);
          roomMemberIdentifiers.add(roomMemberIdentifierToAdd);
          affectedUsers.add(roomMemberIdentifierToAdd);
        }
      }
    } else {
      List<String> affectedUsersList = affectedUsers.stream().map(RoomMemberIdentifier::getSymphonyId).collect(Collectors.toList());
      for (String symphonyId : symphonyIds) {
        roomMemberIdentifiers.removeIf(roomMemberIdentifier -> roomMemberIdentifier.getSymphonyId().equals(symphonyId));
        if(!affectedUsersList.contains(symphonyId)){
          affectedUsers.add(getRoomMemberIdentifierFromSymphonyId(symphonyId));
        }
      }
    }

    String textMessage = generateJoinOrLeaveTextMessage(affectedUsers, roomMemberIdentifiers, isJoining);
    Map<String, List<String>> empSymphonyIdMap = roomMemberIdentifiers.stream()
      .filter(RoomMemberIdentifier::isFederatedUser)
      // Send this notification only to the members already in the room
      // The joiners and leavers will receive another personalized notification
      .filter(roomMemberIdentifier -> !symphonyIds.contains(roomMemberIdentifier.getSymphonyId()))
      .collect(Collectors.groupingBy(RoomMemberIdentifier::getEmp, Collectors.mapping(RoomMemberIdentifier::getSymphonyId, Collectors.toList())));
    for(Map.Entry<String, List<String>> empSymphonyIds : empSymphonyIdMap.entrySet()){
      List<ChannelIdentifier> channels = empSymphonyIds.getValue().stream().map(symphonyId -> new ChannelIdentifier().symphonyId(symphonyId).streamId(streamId)).collect(Collectors.toList());
      LOG.info("Notify Emp Room Members new Joiners or Leavers | emp={} channels={}", empSymphonyIds.getKey(), channels);
      empClient.sendSystemMessageToChannels(empSymphonyIds.getKey(), channels, textMessage, true);
    }
  }

  private List<RoomMemberIdentifier> getRoomMembersIdentifiers(String streamId){
    Optional<RoomMembersIdentifiersResponse> roomMembersIdentifiersResponseOptional = adminClient.getRoomMembersIdentifiers(streamId);
    if(roomMembersIdentifiersResponseOptional.isEmpty()) {
      LOG.error("Get Room Members Info Fail | streamId={}", streamId);
      return new ArrayList<>();
    }
    return roomMembersIdentifiersResponseOptional.get().getMembers();
  }

  private String generateJoinOrLeaveTextMessage(List<RoomMemberIdentifier> affectedUsers, List<RoomMemberIdentifier> roomMemberIdentifiers, boolean isJoining){
    StringBuilder text = new StringBuilder();
    for (RoomMemberIdentifier user : affectedUsers) {
      if (isJoining) {
        text.append(messageSource.getMessage("other.user.joined.room", new Object[]{user.getFirstName(), user.getLastName()}, Locale.getDefault()));
      } else {
        text.append(messageSource.getMessage("other.user.left.room", new Object[]{user.getFirstName(), user.getLastName()}, Locale.getDefault()));
      }
    }
    String membersListAsString = roomMemberIdentifiers.stream()
      .map(roomMemberIdentifier -> roomMemberIdentifier.getFirstName() + " " + roomMemberIdentifier.getLastName()).collect(Collectors.joining(", "));

    text.append(messageSource.getMessage("room.member.list", new Object[]{membersListAsString}, Locale.getDefault()));
    return text.toString();
  }

  private RoomMemberIdentifier getRoomMemberIdentifierFromSymphonyId(String symphonyId){
    Optional<FederatedAccount> optional = federatedAccountRepository.findBySymphonyId(symphonyId);
    if(optional.isPresent()){
      FederatedAccount federatedAccount = optional.get();
      return new RoomMemberIdentifier()
        .symphonyId(symphonyId)
        .firstName(federatedAccount.getFirstName())
        .lastName(federatedAccount.getLastName())
        .federatedUser(true)
        .emp(federatedAccount.getEmp());
    } else {
      UserInfo userInfo = new UserInfo();
      try {
        SymphonySession botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());
        userInfo = getUserInfo(symphonyId, botSession);
      } catch (IllegalStateException e) {
        userInfo.setFirstName("Unknown");
        userInfo.setLastName("User");
      }
      return new RoomMemberIdentifier()
        .symphonyId(symphonyId)
        .firstName(userInfo.getFirstName())
        .lastName(userInfo.getLastName())
        .federatedUser(false)
        .emp(null);
    }

  }
}
