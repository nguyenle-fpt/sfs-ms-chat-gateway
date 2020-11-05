package com.symphony.sfs.ms.chat.service;

import com.google.common.annotations.VisibleForTesting;
import com.symphony.sfs.ms.chat.datafeed.DatafeedListener;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.generated.model.AddRoomMemberFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.CreateRoomFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.FederatedAccountNotFoundProblem;
import com.symphony.sfs.ms.chat.generated.model.RoomMemberRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomMemberResponse;
import com.symphony.sfs.ms.chat.generated.model.RoomRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomResponse;
import com.symphony.sfs.ms.chat.mapper.RoomDtoMapper;
import com.symphony.sfs.ms.chat.mapper.RoomMemberDtoMapper;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.security.StaticSessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyRoom;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyRoomAttributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

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

  private final EmpClient empClient;


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

    RoomResponse roomResponse = RoomDtoMapper.MAPPER.roomRequestToRoomResponse(roomRequest);
    roomResponse.setStreamId(symphonyRoom.getRoomSystemInfo().getId());

    return roomResponse;

  }

  public RoomMemberResponse addRoomMember(String streamId, RoomMemberRequest roomMemberRequest) {

    // Add member in SymphonyRoom before delegating to EMPs because EMPs could send messages in the room directly after the room member is added
    SymphonySession botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());

    // If member is already part of the room, we receive the following response from Symphony:
    // "Member already part of the room or Xpod request will be processed asynchronously"
    // It is not an error so we can ignore it
    streamService.addRoomMember(podConfiguration.getUrl(), new StaticSessionSupplier<>(botSession), streamId, roomMemberRequest.getSymphonyId()).orElseThrow(AddRoomMemberFailedProblem::new);

    RoomMemberResponse roomMemberResponse = RoomMemberDtoMapper.MAPPER.roomMemberRequestToRoomMemberResponse(roomMemberRequest);
    roomMemberResponse.setStreamId(streamId);

    if (roomMemberRequest.isFederatedUser()) {
      FederatedAccount federatedAccount = federatedAccountRepository.findBySymphonyId(roomMemberRequest.getSymphonyId()).orElseThrow(FederatedAccountNotFoundProblem::new);
      roomMemberResponse.setFederatedUserId(federatedAccount.getFederatedUserId());
      roomMemberResponse.setEmp(federatedAccount.getEmp());
      roomMemberResponse.setEmailAddress(federatedAccount.getEmailAddress());
      roomMemberResponse.setPhoneNumber(federatedAccount.getPhoneNumber());

      // Delegate to EMP
      com.symphony.sfs.ms.emp.generated.model.RoomMemberRequest empRoomMemberRequest = RoomMemberDtoMapper.MAPPER.toEmpRoomMemberRequest(roomMemberRequest, federatedAccount);
      // TODO If EMP error maybe indicate that the user has been added on Symphony side but not EMP side?
      empClient.addRoomMember(streamId, federatedAccount.getEmp(), empRoomMemberRequest).orElseThrow(AddRoomMemberFailedProblem::new);
    }

    return roomMemberResponse;
  }
}
