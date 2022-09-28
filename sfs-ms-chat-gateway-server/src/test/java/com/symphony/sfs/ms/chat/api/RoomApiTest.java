package com.symphony.sfs.ms.chat.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.symphony.sfs.ms.admin.generated.model.RoomMemberIdentifier;
import com.symphony.sfs.ms.admin.generated.model.RoomMembersIdentifiersResponse;
import com.symphony.sfs.ms.chat.api.util.AbstractIntegrationTest;
import com.symphony.sfs.ms.chat.generated.model.ReactivateRoomNotImplementedProblem;
import com.symphony.sfs.ms.chat.generated.model.RenameRoomFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.RenameRoomRequest;
import com.symphony.sfs.ms.chat.generated.model.RenameRoomResponse;
import com.symphony.sfs.ms.chat.generated.model.RoomMemberRemoveRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomMemberRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomMemberResponse;
import com.symphony.sfs.ms.chat.generated.model.RoomRemoveRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomResponse;
import com.symphony.sfs.ms.chat.generated.model.SendRoomMembersRequest;
import com.symphony.sfs.ms.chat.generated.model.UpdateRoomActivityMemberRequest;
import com.symphony.sfs.ms.chat.generated.model.UpdateRoomActivityMemberResponse;
import com.symphony.sfs.ms.chat.generated.model.UpdateRoomActivityRequest;
import com.symphony.sfs.ms.chat.generated.model.UpdateRoomActivityResponse;
import com.symphony.sfs.ms.chat.mapper.RoomMemberDtoMapper;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.service.external.MockEmpClient;
import com.symphony.sfs.ms.emp.generated.model.ChannelIdentifier;
import com.symphony.sfs.ms.emp.generated.model.DeleteChannelResponse;
import com.symphony.sfs.ms.emp.generated.model.DeleteChannelsResponse;
import com.symphony.sfs.ms.starter.config.ExceptionHandling;
import com.symphony.sfs.ms.starter.security.SessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyResponse;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyRoom;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyRoomAttributes;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyRoomSystemInfo;
import com.symphony.sfs.ms.starter.util.BulkRemovalStatus;
import com.symphony.sfs.ms.starter.util.UserIdUtils;
import com.symphony.sfs.ms.starter.webclient.WebCallException;
import model.UserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.zalando.problem.Problem;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.symphony.sfs.ms.chat.generated.api.RoomApi.ADDROOMMEMBER_ENDPOINT;
import static com.symphony.sfs.ms.chat.generated.api.RoomApi.CREATEROOM_ENDPOINT;
import static com.symphony.sfs.ms.chat.generated.api.RoomApi.DELETEROOM_ENDPOINT;
import static com.symphony.sfs.ms.chat.generated.api.RoomApi.REMOVEMEMBER_ENDPOINT;
import static com.symphony.sfs.ms.chat.generated.api.RoomApi.RENAMEROOM_ENDPOINT;
import static com.symphony.sfs.ms.chat.generated.api.RoomApi.SENDROOMMEMBERSLISTTOEMPUSER_ENDPOINT;
import static com.symphony.sfs.ms.chat.generated.api.RoomApi.UPDATEROOMACTIVITY_ENDPOINT;
import static com.symphony.sfs.ms.chat.util.HttpRequestUtils.postRequestFail;
import static com.symphony.sfs.ms.starter.testing.MockMvcUtils.configuredGiven;
import static com.symphony.sfs.ms.starter.testing.MockitoUtils.once;
import static com.symphony.sfs.ms.starter.testing.MockitoUtils.twice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomApiTest extends AbstractIntegrationTest {

  private static final String FEDERATION_POD_ID = "1";
  private static final String CLIENT_POD_ID = "2";
  private static final String ROOM_NAME = "roomName";
  private static final String ROOM_STREAM_ID = "streamId";

  private RoomApi roomApi;
  private SymphonySession botSession;

  @BeforeEach
  public void setUp() {
    this.roomApi = new RoomApi(roomService);

    botSession = new SymphonySession("username", "kmToken", "sessionToken");
    when(authenticationService.authenticate(any(), any(), eq(botConfiguration.getUsername()), anyString())).thenReturn(botSession);
  }
  ////////////////
  // createRoom //
  ////////////////
  @Test
  public void createRoom_SymphonyFail() {

    SymphonyRoomAttributes roomAttributes = SymphonyRoomAttributes.builder()
      .name(ROOM_NAME)
      .description(ROOM_NAME)
//      .keywords(Arrays.asList(SymphonyKeyword.builder().key("key1").value("value1").build()))
      .membersCanInvite(false)
      .discoverable(false)
      .isPublic(false)
      .readOnly(false)
      .copyProtected(false)
      .crossPod(true)
      .viewHistory(false)
      .multiLateralRoom(false)
      .build();

    String podUrl = podConfiguration.getUrl();
    doReturn(Optional.empty()).when(streamService).createRoom(eq(podUrl), any(SessionSupplier.class), eq(roomAttributes));

    RoomRequest roomRequest = new RoomRequest().roomName(ROOM_NAME);
    createRoomFail(roomRequest, com.symphony.sfs.ms.chat.generated.model.CreateRoomFailedProblem.class.getName(), HttpStatus.INTERNAL_SERVER_ERROR);

  }

  @Test
  public void createRoom_OK() {

    SymphonyRoomAttributes roomAttributes = SymphonyRoomAttributes.builder()
      .name(ROOM_NAME)
      .description(ROOM_NAME)
//      .keywords(Arrays.asList(SymphonyKeyword.builder().key("key1").value("value1").build()))
      .membersCanInvite(false)
      .discoverable(false)
      .isPublic(false)
      .readOnly(false)
      .copyProtected(false)
      .crossPod(true)
      .viewHistory(false)
      .multiLateralRoom(false)
      .build();


    Long creationDate = new Date().getTime();

    SymphonyRoomSystemInfo roomSystemInfo = SymphonyRoomSystemInfo.builder()
      .active(true)
      .creationDate(creationDate)
      .createdByUserId(UserIdUtils.buildUserId(1, 1))
      .id(ROOM_STREAM_ID)
      .build();

    String podUrl = podConfiguration.getUrl();

    SymphonyRoom symphonyRoom = SymphonyRoom.builder()
      .roomAttributes(roomAttributes)
      .roomSystemInfo(roomSystemInfo)
      .build();

    doReturn(Optional.of(symphonyRoom)).when(streamService).createRoom(eq(podUrl), any(SessionSupplier.class), eq(roomAttributes));

    RoomRequest roomRequest = new RoomRequest().roomName(ROOM_NAME);
    RoomResponse roomResponse = createRoom(roomRequest);

    assertEquals(ROOM_STREAM_ID, roomResponse.getStreamId());
    assertEquals(ROOM_NAME, roomResponse.getRoomName());

  }

  ////////////////
  // renameRoom //
  ////////////////

  @Test
  public void renameRoom_failedGetRoom() {
    String podUrl = podConfiguration.getUrl();
    doReturn(Optional.empty()).when(streamService).roomInfo(eq(podUrl), any(SessionSupplier.class), eq("streamId"));

    RenameRoomRequest request = new RenameRoomRequest().newRoomName("newRoomName");

    renameRoomFail("streamId", request, RenameRoomFailedProblem.class.getName(), HttpStatus.INTERNAL_SERVER_ERROR);

    verify(streamService, once()).roomInfo(eq(podUrl), any(SessionSupplier.class), eq("streamId"));
    verify(streamService, never()).updateRoom(eq(podUrl), any(SessionSupplier.class), anyString(), any(SymphonyRoomAttributes.class));
  }

  @Test
  public void renameRoom_failedUpdateRoom() {
    String podUrl = podConfiguration.getUrl();
    SymphonyRoomAttributes oldRoomAttributes = SymphonyRoomAttributes.builder()
      .name("roomName")
      .description("roomName")
      .build();
    SymphonyRoom oldSymphonyRoom = SymphonyRoom.builder().roomAttributes(oldRoomAttributes).build();
    SymphonyRoomAttributes newRoomAttributes = SymphonyRoomAttributes.builder()
      .name("newRoomName")
      .description("newRoomName")
      .build();
    doReturn(Optional.of(oldSymphonyRoom)).when(streamService).roomInfo(eq(podUrl), any(SessionSupplier.class), eq("streamId"));
    doReturn(Optional.empty()).when(streamService).updateRoom(eq(podUrl), any(SessionSupplier.class), eq("streamId"), eq(newRoomAttributes));

    RenameRoomRequest request = new RenameRoomRequest().newRoomName("newRoomName");

    renameRoomFail("streamId", request, RenameRoomFailedProblem.class.getName(), HttpStatus.INTERNAL_SERVER_ERROR);

    verify(streamService, once()).roomInfo(eq(podUrl), any(SessionSupplier.class), eq("streamId"));
    verify(streamService, once()).updateRoom(eq(podUrl), any(SessionSupplier.class), anyString(), any(SymphonyRoomAttributes.class));
  }

  @Test
  public void renameRoom_Ok() {
    String podUrl = podConfiguration.getUrl();
    SymphonyRoomAttributes oldRoomAttributes = SymphonyRoomAttributes.builder()
      .name("roomName")
      .description("roomName")
      .build();
    SymphonyRoom oldSymphonyRoom = SymphonyRoom.builder().roomAttributes(oldRoomAttributes).build();
    SymphonyRoomAttributes newRoomAttributes = SymphonyRoomAttributes.builder()
      .name("newRoomName")
      .description("newRoomName")
      .build();
    SymphonyRoom newSymphonyRoom = SymphonyRoom.builder().roomAttributes(newRoomAttributes).build();
    doReturn(Optional.of(oldSymphonyRoom)).when(streamService).roomInfo(eq(podUrl), any(SessionSupplier.class), eq("streamId"));
    doReturn(Optional.of(newSymphonyRoom)).when(streamService).updateRoom(eq(podUrl), any(SessionSupplier.class), eq("streamId"), eq(newRoomAttributes));

    RenameRoomRequest request = new RenameRoomRequest().newRoomName("newRoomName");

    RenameRoomResponse response = this.renameRoom("streamId", request);

    RenameRoomResponse expectedResponse = new RenameRoomResponse().newRoomName("newRoomName");
    assertEquals(expectedResponse, response);
    verify(streamService, once()).roomInfo(eq(podUrl), any(SessionSupplier.class), eq("streamId"));
    verify(streamService, once()).updateRoom(eq(podUrl), any(SessionSupplier.class), eq("streamId"), eq(newRoomAttributes));
  }

  //////////////////////////
  // update room Activity //
  //////////////////////////

  @Test
  public void updateRoomActivity() {
    createRoom_OK();

    List<UpdateRoomActivityMemberRequest> members = Arrays.asList(
      new UpdateRoomActivityMemberRequest().emp("EMP1").federatedUser(true).symphonyId(symphonyId("31", CLIENT_POD_ID)),
      new UpdateRoomActivityMemberRequest().emp("EMP1").federatedUser(true).symphonyId(symphonyId("32", CLIENT_POD_ID)),
      new UpdateRoomActivityMemberRequest().emp("EMP2").federatedUser(true).symphonyId(symphonyId("33", CLIENT_POD_ID)),
      new UpdateRoomActivityMemberRequest().emp(null).federatedUser(false).symphonyId(symphonyId("11", CLIENT_POD_ID))
    );
    UpdateRoomActivityRequest updateRoomActivityRequest = new UpdateRoomActivityRequest().setActive(false).members(members);


    when(empClient.deleteChannels(Arrays.asList(
      new ChannelIdentifier().streamId(ROOM_STREAM_ID).symphonyId(symphonyId("31", CLIENT_POD_ID)),
      new ChannelIdentifier().streamId(ROOM_STREAM_ID).symphonyId(symphonyId("32", CLIENT_POD_ID))
    ), "EMP1")).thenReturn(Optional.of(new DeleteChannelsResponse().report(Arrays.asList(
        new DeleteChannelResponse().streamId(ROOM_STREAM_ID).symphonyId(symphonyId("31", CLIENT_POD_ID)).status(BulkRemovalStatus.SUCCESS),
        new DeleteChannelResponse().streamId(ROOM_STREAM_ID).symphonyId(symphonyId("32", CLIENT_POD_ID)).status(BulkRemovalStatus.NOT_FOUND)
    ))));

    when(empClient.deleteChannels(Collections.singletonList(
      new ChannelIdentifier().streamId(ROOM_STREAM_ID).symphonyId(symphonyId("33", CLIENT_POD_ID))
    ), "EMP2")).thenReturn(Optional.of(new DeleteChannelsResponse().report(Collections.singletonList(
      new DeleteChannelResponse().streamId(ROOM_STREAM_ID).symphonyId(symphonyId("33", CLIENT_POD_ID)).status(BulkRemovalStatus.SUCCESS)
    ))));

    UpdateRoomActivityResponse response = updateRoomActivity(ROOM_STREAM_ID, updateRoomActivityRequest);

    UpdateRoomActivityResponse expectedResponse = new UpdateRoomActivityResponse().members(Arrays.asList(
      new UpdateRoomActivityMemberResponse().emp("EMP1").federatedUser(true).symphonyId(symphonyId("31", CLIENT_POD_ID)).status(BulkRemovalStatus.SUCCESS),
      new UpdateRoomActivityMemberResponse().emp("EMP1").federatedUser(true).symphonyId(symphonyId("32", CLIENT_POD_ID)).status(BulkRemovalStatus.NOT_FOUND),
      new UpdateRoomActivityMemberResponse().emp("EMP2").federatedUser(true).symphonyId(symphonyId("33", CLIENT_POD_ID)).status(BulkRemovalStatus.SUCCESS),
      new UpdateRoomActivityMemberResponse().emp(null).federatedUser(false).symphonyId(symphonyId("11", CLIENT_POD_ID)).status(BulkRemovalStatus.SUCCESS)
      ));

    assertEquals(expectedResponse, response);
  }


  @Test
  public void updateRoomActivity_reactivateRoom() {
    createRoom_OK();

    List<UpdateRoomActivityMemberRequest> members = Arrays.asList(
      new UpdateRoomActivityMemberRequest().emp("EMP1").federatedUser(true).symphonyId(symphonyId("31", CLIENT_POD_ID)),
      new UpdateRoomActivityMemberRequest().emp("EMP1").federatedUser(true).symphonyId(symphonyId("32", CLIENT_POD_ID)),
      new UpdateRoomActivityMemberRequest().emp("EMP2").federatedUser(true).symphonyId(symphonyId("33", CLIENT_POD_ID)),
      new UpdateRoomActivityMemberRequest().emp(null).federatedUser(false).symphonyId(symphonyId("11", CLIENT_POD_ID))
    );
    UpdateRoomActivityRequest updateRoomActivityRequest = new UpdateRoomActivityRequest().setActive(true).members(members);

    updateRoomActivityFail(ROOM_STREAM_ID, updateRoomActivityRequest, ReactivateRoomNotImplementedProblem.class.getName(), HttpStatus.NOT_IMPLEMENTED);

  }

  @Test
  public void updateRoomActivity_empError() {
    createRoom_OK();

    List<UpdateRoomActivityMemberRequest> members = Arrays.asList(
      new UpdateRoomActivityMemberRequest().emp("EMP1").federatedUser(true).symphonyId(symphonyId("31", CLIENT_POD_ID)),
      new UpdateRoomActivityMemberRequest().emp("EMP1").federatedUser(true).symphonyId(symphonyId("32", CLIENT_POD_ID)),
      new UpdateRoomActivityMemberRequest().emp("EMP2").federatedUser(true).symphonyId(symphonyId("33", CLIENT_POD_ID)),
      new UpdateRoomActivityMemberRequest().emp(null).federatedUser(false).symphonyId(symphonyId("11", CLIENT_POD_ID))
    );
    UpdateRoomActivityRequest updateRoomActivityRequest = new UpdateRoomActivityRequest().setActive(false).members(members);


    when(empClient.deleteChannels(Arrays.asList(
      new ChannelIdentifier().streamId(ROOM_STREAM_ID).symphonyId(symphonyId("31", CLIENT_POD_ID)),
      new ChannelIdentifier().streamId(ROOM_STREAM_ID).symphonyId(symphonyId("32", CLIENT_POD_ID))
    ), "EMP1")).thenThrow(RuntimeException.class);

    when(empClient.deleteChannels(Collections.singletonList(
      new ChannelIdentifier().streamId(ROOM_STREAM_ID).symphonyId(symphonyId("33", CLIENT_POD_ID))
    ), "EMP2")).thenReturn(Optional.of(new DeleteChannelsResponse().report(Collections.singletonList(
      new DeleteChannelResponse().streamId(ROOM_STREAM_ID).symphonyId(symphonyId("33", CLIENT_POD_ID)).status(BulkRemovalStatus.SUCCESS)
    ))));

    UpdateRoomActivityResponse response = updateRoomActivity(ROOM_STREAM_ID, updateRoomActivityRequest);

    UpdateRoomActivityResponse expectedResponse = new UpdateRoomActivityResponse().members(Arrays.asList(
      new UpdateRoomActivityMemberResponse().emp("EMP1").federatedUser(true).symphonyId(symphonyId("31", CLIENT_POD_ID)).status(BulkRemovalStatus.FAILURE),
      new UpdateRoomActivityMemberResponse().emp("EMP1").federatedUser(true).symphonyId(symphonyId("32", CLIENT_POD_ID)).status(BulkRemovalStatus.FAILURE),
      new UpdateRoomActivityMemberResponse().emp("EMP2").federatedUser(true).symphonyId(symphonyId("33", CLIENT_POD_ID)).status(BulkRemovalStatus.SUCCESS),
      new UpdateRoomActivityMemberResponse().emp(null).federatedUser(false).symphonyId(symphonyId("11", CLIENT_POD_ID)).status(BulkRemovalStatus.SUCCESS)
    ));

    assertEquals(expectedResponse, response);
  }


  /////////////////////
  // add room member //
  /////////////////////
  @Test
  public void addRoomMember_SymphonyFail() {
    createRoom_OK();

    String podUrl = podConfiguration.getUrl();
    doReturn(Optional.empty()).when(streamService).addRoomMember(eq(podUrl), any(SessionSupplier.class), anyString(), anyString());

    RoomMemberRequest roomMemberRequest = new RoomMemberRequest().clientPodId(CLIENT_POD_ID).symphonyId(symphonyId("21", CLIENT_POD_ID)).federatedUser(false).roomName(ROOM_NAME);
    addRoomMemberFail(ROOM_STREAM_ID, roomMemberRequest, com.symphony.sfs.ms.chat.generated.model.AddRoomMemberFailedProblem.class.getName(), HttpStatus.INTERNAL_SERVER_ERROR);

  }

  @Test
  public void addRoomMember_FederatedAccountNotFound() {

    String podUrl = podConfiguration.getUrl();
    doReturn(Optional.of(new SymphonyResponse())).when(streamService).addRoomMember(eq(podUrl), any(SessionSupplier.class), anyString(), anyString());

    SymphonyResponse symphonyResponse = SymphonyResponse.builder().format("TEXT").message("Member added").build();
    doReturn(Optional.of(symphonyResponse)).when(streamService).addRoomMember(eq(podUrl), any(SessionSupplier.class), eq(ROOM_STREAM_ID), eq(symphonyId("11", FEDERATION_POD_ID)));

    RoomMemberRequest roomMemberRequest = new RoomMemberRequest().clientPodId(CLIENT_POD_ID).symphonyId(symphonyId("11", FEDERATION_POD_ID)).federatedUser(true).roomName(ROOM_NAME);
    addRoomMemberFail(ROOM_STREAM_ID, roomMemberRequest, com.symphony.sfs.ms.chat.generated.model.UnknownFederatedAccountProblem.class.getName(), HttpStatus.BAD_REQUEST);

  }

  @Test
  public void addRoomMember_OK() {

    createRoom_OK();

    UserInfo advisorInfo = new UserInfo();
    advisorInfo.setFirstName("firstName");
    advisorInfo.setLastName("lastName");
    advisorInfo.setDisplayName("displayName");
    advisorInfo.setCompany("companyName");

    when(usersInfoService.getUserFromId(any(), any(), any())).thenReturn(Optional.of(advisorInfo));

    String podUrl = podConfiguration.getUrl();

    SymphonyResponse symphonyResponse = SymphonyResponse.builder().format("TEXT").message("Member added").build();
    doReturn(Optional.of(symphonyResponse)).when(streamService).addRoomMember(eq(podUrl), any(SessionSupplier.class), eq(ROOM_STREAM_ID), eq(symphonyId("11", FEDERATION_POD_ID)));

    FederatedAccount federatedAccount = federatedAccountRepository.save(FederatedAccount.builder()
      .symphonyUserId(symphonyId("11", FEDERATION_POD_ID))
      .federatedUserId(federatedUserId("11"))
      .emp(emp("11"))
      .phoneNumber((phoneNumber("11")))
      .build());
    RoomMemberRequest roomMemberRequest = new RoomMemberRequest().clientPodId(CLIENT_POD_ID).symphonyId(symphonyId("11", FEDERATION_POD_ID)).federatedUser(true).roomName(ROOM_NAME).empChannelConnector("empChannelConnector");
    RoomMemberResponse roomMemberResponse = addRoomMember(ROOM_STREAM_ID, roomMemberRequest);


    assertEquals(ROOM_STREAM_ID, roomMemberResponse.getStreamId());
    assertEquals(symphonyId("11", FEDERATION_POD_ID), roomMemberResponse.getSymphonyId());
    assertEquals(federatedUserId("11"), roomMemberResponse.getFederatedUserId());
    assertEquals(emp("11"), roomMemberResponse.getEmp());
    assertEquals(phoneNumber("11"), roomMemberResponse.getPhoneNumber());

    com.symphony.sfs.ms.emp.generated.model.RoomMemberRequest empRoomMemberRequest = RoomMemberDtoMapper.MAPPER.toEmpRoomMemberRequest(roomMemberRequest, federatedAccount, advisorInfo);
    assertEquals("empChannelConnector", empRoomMemberRequest.getEmpChannelConnector());
    verify(empClient, once()).addRoomMemberOrFail(ROOM_STREAM_ID,  emp("11"), empRoomMemberRequest);
    verify(streamService, never()).removeRoomMember(any(), any(), any(), any());
  }
  @Test
  public void addRoomMember_OK_attachmentsFlag() {

    createRoom_OK();

    UserInfo advisorInfo = new UserInfo();
    advisorInfo.setFirstName("firstName");
    advisorInfo.setLastName("lastName");
    advisorInfo.setDisplayName("displayName");
    advisorInfo.setCompany("companyName");

    when(usersInfoService.getUserFromId(any(), any(), any())).thenReturn(Optional.of(advisorInfo));

    String podUrl = podConfiguration.getUrl();

    SymphonyResponse symphonyResponse = SymphonyResponse.builder().format("TEXT").message("Member added").build();
    doReturn(Optional.of(symphonyResponse)).when(streamService).addRoomMember(eq(podUrl), any(SessionSupplier.class), eq(ROOM_STREAM_ID), eq(symphonyId("11", FEDERATION_POD_ID)));

    FederatedAccount federatedAccount = federatedAccountRepository.save(FederatedAccount.builder()
      .symphonyUserId(symphonyId("11", FEDERATION_POD_ID))
      .federatedUserId(federatedUserId("11"))
      .emp(emp("11"))
      .phoneNumber((phoneNumber("11")))
      .build());
    RoomMemberRequest roomMemberRequest = new RoomMemberRequest().clientPodId(CLIENT_POD_ID).symphonyId(symphonyId("11", FEDERATION_POD_ID)).federatedUser(true).roomName(ROOM_NAME).attachmentsDisabled(true);
    RoomMemberResponse roomMemberResponse = addRoomMember(ROOM_STREAM_ID, roomMemberRequest);


    assertEquals(ROOM_STREAM_ID, roomMemberResponse.getStreamId());
    assertEquals(symphonyId("11", FEDERATION_POD_ID), roomMemberResponse.getSymphonyId());
    assertEquals(federatedUserId("11"), roomMemberResponse.getFederatedUserId());
    assertEquals(emp("11"), roomMemberResponse.getEmp());
    assertEquals(phoneNumber("11"), roomMemberResponse.getPhoneNumber());

    com.symphony.sfs.ms.emp.generated.model.RoomMemberRequest empRoomMemberRequest = RoomMemberDtoMapper.MAPPER.toEmpRoomMemberRequest(roomMemberRequest, federatedAccount, advisorInfo);
    verify(empClient, once()).addRoomMemberOrFail(ROOM_STREAM_ID,  emp("11"), empRoomMemberRequest);
    verify(streamService, never()).removeRoomMember(any(), any(), any(), any());
    assertEquals(true, empRoomMemberRequest.isAttachmentsDisabled());
  }

  @Test
  public void addRoomMember_OK_withFederationGroup() {

    createRoom_OK();

    UserInfo advisorInfo = new UserInfo();
    advisorInfo.setFirstName("firstName");
    advisorInfo.setLastName("lastName");
    advisorInfo.setDisplayName("displayName");
    advisorInfo.setCompany("companyName");

    when(usersInfoService.getUserFromId(any(), any(), any())).thenReturn(Optional.of(advisorInfo));

    String podUrl = podConfiguration.getUrl();

    SymphonyResponse symphonyResponse = SymphonyResponse.builder().format("TEXT").message("Member added").build();
    doReturn(Optional.of(symphonyResponse)).when(streamService).addRoomMember(eq(podUrl), any(SessionSupplier.class), eq(ROOM_STREAM_ID), eq(symphonyId("11", FEDERATION_POD_ID)));

    FederatedAccount federatedAccount = federatedAccountRepository.save(FederatedAccount.builder()
      .symphonyUserId(symphonyId("11", FEDERATION_POD_ID))
      .federatedUserId(federatedUserId("11"))
      .emp(emp("11"))
      .phoneNumber((phoneNumber("11")))
      .build());
    RoomMemberRequest roomMemberRequest = new RoomMemberRequest().clientPodId(CLIENT_POD_ID).symphonyId(symphonyId("11", FEDERATION_POD_ID)).federatedUser(true).roomName(ROOM_NAME).federationGroupId("federationGroupId");
    RoomMemberResponse roomMemberResponse = addRoomMember(ROOM_STREAM_ID, roomMemberRequest);


    assertEquals(ROOM_STREAM_ID, roomMemberResponse.getStreamId());
    assertEquals(symphonyId("11", FEDERATION_POD_ID), roomMemberResponse.getSymphonyId());
    assertEquals(federatedUserId("11"), roomMemberResponse.getFederatedUserId());
    assertEquals(emp("11"), roomMemberResponse.getEmp());
    assertEquals(phoneNumber("11"), roomMemberResponse.getPhoneNumber());

    com.symphony.sfs.ms.emp.generated.model.RoomMemberRequest empRoomMemberRequest = RoomMemberDtoMapper.MAPPER.toEmpRoomMemberRequest(roomMemberRequest, federatedAccount, advisorInfo);
    verify(empClient, once()).addRoomMemberOrFail(ROOM_STREAM_ID,  emp("11"), empRoomMemberRequest);
    verify(streamService, never()).removeRoomMember(any(), any(), any(), any());
  }

  @Test
  public void addRoomMember_ErrorCreatingChannel() throws JsonProcessingException, URISyntaxException {

    createRoom_OK();

    UserInfo advisorInfo = new UserInfo();
    advisorInfo.setFirstName("firstName");
    advisorInfo.setLastName("lastName");
    advisorInfo.setDisplayName("displayName");
    advisorInfo.setCompany("companyName");

    when(usersInfoService.getUserFromId(any(), any(), any())).thenReturn(Optional.of(advisorInfo));
    String podUrl = podConfiguration.getUrl();

    SymphonyResponse symphonyResponse = SymphonyResponse.builder().format("TEXT").message("Member added").build();
    doReturn(Optional.of(symphonyResponse)).when(streamService).addRoomMember(eq(podUrl), any(SessionSupplier.class), eq(ROOM_STREAM_ID), eq(symphonyId("11", FEDERATION_POD_ID)));

    FederatedAccount federatedAccount = federatedAccountRepository.save(FederatedAccount.builder()
      .symphonyUserId(symphonyId("11", FEDERATION_POD_ID))
      .federatedUserId(federatedUserId("11"))
      .emp(emp("11"))
      .phoneNumber((phoneNumber("11")))
      .build());

    MockEmpClient mockEmpClient = (MockEmpClient) empClient;
    mockEmpClient.getFederatedUserFailing().add(federatedAccount.getFederatedUserId());


    RoomMemberRequest roomMemberRequest = new RoomMemberRequest().clientPodId(CLIENT_POD_ID).symphonyId(symphonyId("11", FEDERATION_POD_ID)).federatedUser(true).roomName(ROOM_NAME);

    WebClientResponseException wcreWithDetail = WebClientResponseException.create(HttpStatus.INTERNAL_SERVER_ERROR.value(), "statusText", null,
      objectMapper.writeValueAsString(Problem.builder().withType(new URI("http://my.problem.type")).withDetail("detail").build()).getBytes(Charset.defaultCharset()), Charset.defaultCharset());
    WebClientResponseException wcreWithoutDetail = WebClientResponseException.create(HttpStatus.INTERNAL_SERVER_ERROR.value(), "statusText", null,
      objectMapper.writeValueAsString(Problem.builder().withType(new URI("http://my.problem.type")).build()).getBytes(Charset.defaultCharset()), Charset.defaultCharset());

    com.symphony.sfs.ms.emp.generated.model.RoomMemberRequest empRoomMemberRequest = RoomMemberDtoMapper.MAPPER.toEmpRoomMemberRequest(roomMemberRequest, federatedAccount, advisorInfo);
    when(empClient.addRoomMemberOrFail(ROOM_STREAM_ID, emp("11"), empRoomMemberRequest))
      .thenThrow(new WebCallException(objectMapper, wcreWithDetail))
      .thenThrow(new WebCallException(objectMapper, wcreWithoutDetail));

    // Check that Problem is built with detail from underlying problem if available
    addRoomMemberFail(ROOM_STREAM_ID, roomMemberRequest, com.symphony.sfs.ms.chat.generated.model.AddRoomMemberFailedProblem.class.getName(), HttpStatus.INTERNAL_SERVER_ERROR, "detail");
    verify(empClient, once()).addRoomMemberOrFail(ROOM_STREAM_ID,  emp("11"), empRoomMemberRequest);
    verify(streamService, once()).addRoomMember(eq(podUrl), any(SessionSupplier.class), eq(ROOM_STREAM_ID), eq(symphonyId("11", FEDERATION_POD_ID)));
    verify(streamService, once()).removeRoomMember(eq(podUrl), any(SessionSupplier.class), eq(ROOM_STREAM_ID), eq(symphonyId("11", FEDERATION_POD_ID)));


    // Check that Problem is built with problem type of underlying exception if available
    addRoomMemberFail(ROOM_STREAM_ID, roomMemberRequest, com.symphony.sfs.ms.chat.generated.model.AddRoomMemberFailedProblem.class.getName(), HttpStatus.INTERNAL_SERVER_ERROR, "http://my.problem.type");

    verify(empClient, twice()).addRoomMemberOrFail(ROOM_STREAM_ID,  emp("11"), empRoomMemberRequest);
    verify(streamService, twice()).addRoomMember(eq(podUrl), any(SessionSupplier.class), eq(ROOM_STREAM_ID), eq(symphonyId("11", FEDERATION_POD_ID)));
    verify(streamService, twice()).removeRoomMember(eq(podUrl), any(SessionSupplier.class), eq(ROOM_STREAM_ID), eq(symphonyId("11", FEDERATION_POD_ID)));
  }

  @Test
  public void removeRoomMemberNotFederated() {
    String streamId = "streamId";
    String symphonyId = "12345";
    removeRoomMember(streamId, symphonyId, null,true, false);
    String podUrl = podConfiguration.getUrl();
    verify(streamService).removeRoomMember(eq(podUrl), any(SessionSupplier.class), eq(streamId), eq(symphonyId));
    verify(empClient, never()).deleteChannels(any(), any());
  }

  @Test
  public void removeRoomMemberFederatedDontRemoveChannel() {
    String streamId = "streamId";
    String symphonyId = "12345";
    removeRoomMember(streamId, symphonyId, "emp",false, true);
    String podUrl = podConfiguration.getUrl();
    verify(streamService).removeRoomMember(eq(podUrl), any(SessionSupplier.class), eq(streamId), eq(symphonyId));
    verify(empClient, never()).deleteChannels(any(), any());
  }


  @Test
  public void removeRoomMemberFederatedRemoveChannel() {
    String streamId = "streamId";
    String symphonyId = "12345";
    String emp = "emo";
    removeRoomMember(streamId, symphonyId, emp,true, true);
    String podUrl = podConfiguration.getUrl();
    verify(streamService).removeRoomMember(eq(podUrl), any(SessionSupplier.class), eq(streamId), eq(symphonyId));

    List<ChannelIdentifier> deleteChannelRequests = Collections.singletonList(
      new ChannelIdentifier().streamId(streamId).symphonyId(symphonyId)
    );

    verify(empClient, times(1)).deleteChannels(deleteChannelRequests, emp);
  }


  @Test
  public void removeRoomDontRemoveChannel() {
    String streamId = "streamId";
    String podUrl = podConfiguration.getUrl();

    List<RoomMemberRemoveRequest> roomMemberRemoveRequest = List.of(
      createRoomMemberRemoveRequest("12345", null, false, false),
      createRoomMemberRemoveRequest("12346", "emp", false, true),
      createRoomMemberRemoveRequest("12347", "emp", false, true),
      createRoomMemberRemoveRequest("12348", null, false, false)
    );

    deleteRoom(roomMemberRemoveRequest, streamId);
    verify(streamService).setRoomActive(eq(podUrl), any(SessionSupplier.class), eq(streamId), eq(false));
    verify(empClient, never()).deleteChannels(any(), any());

  }

  @Test
  public void removeRoomRemoveChannel() {
    String streamId = "streamId";
    String podUrl = podConfiguration.getUrl();

    List<RoomMemberRemoveRequest> roomMemberRemoveRequest = List.of(
      createRoomMemberRemoveRequest("12345", null, true, false),
      createRoomMemberRemoveRequest("12346", "emp", true, true),
      createRoomMemberRemoveRequest("12347", "emp", true, true),
      createRoomMemberRemoveRequest("12348", null, true, false)
    );

    deleteRoom(roomMemberRemoveRequest, streamId);
    verify(streamService).setRoomActive(eq(podUrl), any(SessionSupplier.class), eq(streamId), eq(false));

    List<ChannelIdentifier> deleteChannelRequests = List.of(
      new ChannelIdentifier().streamId(streamId).symphonyId("12346"),
      new ChannelIdentifier().streamId(streamId).symphonyId("12347")
    );

    verify(empClient, times(1)).deleteChannels(deleteChannelRequests, "emp");

  }

  @Test
  public void removeRoomRemoveChannelMultiEMP() {
    String streamId = "streamId";
    String podUrl = podConfiguration.getUrl();

    List<RoomMemberRemoveRequest> roomMemberRemoveRequest = List.of(
      createRoomMemberRemoveRequest("12345", null, true, false),
      createRoomMemberRemoveRequest("12346", "emp", true, true),
      createRoomMemberRemoveRequest("12347", "emp2", true, true),
      createRoomMemberRemoveRequest("12348", null, true, false)
    );

    deleteRoom(roomMemberRemoveRequest, streamId);
    verify(streamService).setRoomActive(eq(podUrl), any(SessionSupplier.class), eq(streamId), eq(false));

    List<ChannelIdentifier> deleteChannelRequests = List.of(
      new ChannelIdentifier().streamId(streamId).symphonyId("12346")
    );
    List<ChannelIdentifier> deleteChannelRequestsSecondEmp = List.of(
      new ChannelIdentifier().streamId(streamId).symphonyId("12347")
    );

    verify(empClient, times(1)).deleteChannels(deleteChannelRequests, "emp");
    verify(empClient, times(1)).deleteChannels(deleteChannelRequestsSecondEmp, "emp2");

  }



  /////////////
  // helpers //
  /////////////

  private void removeRoomMember(String streamId, String symphonyId, String emp, boolean removeChannel, boolean federatedUser) {
    RoomMemberRemoveRequest roomMemberRemoveRequest = createRoomMemberRemoveRequest(symphonyId, emp, removeChannel, federatedUser);

    configuredGiven(objectMapper, new ExceptionHandling(null), roomApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(roomMemberRemoveRequest)
      .when()
      .delete(REMOVEMEMBER_ENDPOINT, streamId)
      .then()
      .statusCode(HttpStatus.OK.value())
    ;
  }

  private RoomMemberRemoveRequest createRoomMemberRemoveRequest(String symphonyId, String emp, boolean removeChannel, boolean federatedUser) {
    return new RoomMemberRemoveRequest()
      .removeChannel(removeChannel)
      .federatedUser(federatedUser)
      .emp(emp)
      .symphonyId(symphonyId);
  }

  private void deleteRoom(List<RoomMemberRemoveRequest> members, String streamId) {

    RoomRemoveRequest roomRemoveRequest = new RoomRemoveRequest().members(members);

    configuredGiven(objectMapper, new ExceptionHandling(null), roomApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(roomRemoveRequest)
      .when()
      .delete(DELETEROOM_ENDPOINT, streamId)
      .then()
      .statusCode(HttpStatus.OK.value())
    ;
  }


  private RoomResponse createRoom(RoomRequest roomRequest) {
    return configuredGiven(objectMapper, new ExceptionHandling(null), roomApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(roomRequest)
      .when()
      .post(CREATEROOM_ENDPOINT)
      .then()
      .statusCode(HttpStatus.OK.value())
      .extract().response().body()
      .as(RoomResponse.class);
  }

  private RenameRoomResponse renameRoom(String streamId, RenameRoomRequest request) {
    return configuredGiven(objectMapper, new ExceptionHandling(null), roomApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(request)
      .when()
      .post(RENAMEROOM_ENDPOINT, streamId)
      .then()
      .statusCode(HttpStatus.OK.value())
      .extract().response().body()
      .as(RenameRoomResponse.class);
  }

  private UpdateRoomActivityResponse updateRoomActivity(String streamId, UpdateRoomActivityRequest updateRoomActivityRequest) {
    return configuredGiven(objectMapper, new ExceptionHandling(null), roomApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(updateRoomActivityRequest)
      .when()
      .post(UPDATEROOMACTIVITY_ENDPOINT, streamId)
      .then()
      .statusCode(HttpStatus.OK.value())
      .extract().response().body()
      .as(UpdateRoomActivityResponse.class);
  }

  private void createRoomFail(RoomRequest roomRequest, String problemClassName, HttpStatus httpStatus) {
    postRequestFail(roomRequest, roomApi, CREATEROOM_ENDPOINT, objectMapper, tracer, problemClassName, httpStatus);
  }

  private void renameRoomFail(String streamId, RenameRoomRequest updateRoomActivityRequest, String problemClassName, HttpStatus httpStatus) {
    postRequestFail(updateRoomActivityRequest, roomApi, RENAMEROOM_ENDPOINT, Collections.singletonList(streamId), objectMapper, tracer, problemClassName, httpStatus);
  }

  private void updateRoomActivityFail(String streamId, UpdateRoomActivityRequest updateRoomActivityRequest, String problemClassName, HttpStatus httpStatus) {
    postRequestFail(updateRoomActivityRequest, roomApi, UPDATEROOMACTIVITY_ENDPOINT, Collections.singletonList(streamId), objectMapper, tracer, problemClassName, httpStatus);
  }

  private RoomMemberResponse addRoomMember(String streamId, RoomMemberRequest roomMemberRequest) {

    return configuredGiven(objectMapper, new ExceptionHandling(null), roomApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(roomMemberRequest)
      .when()
      .post(ADDROOMMEMBER_ENDPOINT, streamId)
      .then()
      .statusCode(HttpStatus.OK.value())
      .extract().response().body()
      .as(RoomMemberResponse.class);
  }

  private void sendRoomMembersListToEmpUser(String streamId, SendRoomMembersRequest sendRoomMembersRequest) {
    configuredGiven(objectMapper, new ExceptionHandling(null), roomApi)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .body(sendRoomMembersRequest)
      .when()
      .post(SENDROOMMEMBERSLISTTOEMPUSER_ENDPOINT, streamId)
      .then()
      .statusCode(HttpStatus.OK.value());
  }

  private void addRoomMemberFail(String streamId, RoomMemberRequest roomRequest, String problemClassName, HttpStatus httpStatus) {
    addRoomMemberFail(streamId, roomRequest, problemClassName, httpStatus, null);
  }

  private void addRoomMemberFail(String streamId, RoomMemberRequest roomRequest, String problemClassName, HttpStatus httpStatus, String problemDetail) {
    postRequestFail(roomRequest, roomApi, ADDROOMMEMBER_ENDPOINT, Collections.singletonList(streamId), objectMapper, tracer, problemClassName, httpStatus, problemDetail);
  }

  private static String phoneNumber(String suffix) {
    return "+336999999" + suffix;
  }

  private static String emailAddress(String suffix) {
    return "email" + suffix + "@symphony.com";
  }

  private static String federatedUserId(String suffix) {
    return "federatedUserId" + suffix;
  }

  private static String symphonyId(String suffix, String podId) {
    return UserIdUtils.buildUserId("" + suffix, podId);
  }

  private static String emp(String suffix) {
    return "emp" + suffix;
  }

  private static String generateMessageToSendJoining(String roomName, List<String> roomMembersSuffix, boolean roomNameFound){
    return generateMessageToSend(roomName, roomMembersSuffix, true, roomNameFound);
  }

  private static String generateMessageToSendNotJoining(List<String> roomMembersSuffix){
    return generateMessageToSend(null, roomMembersSuffix, false, false);
  }

  private static String generateMessageToSend(String roomName, List<String> roomMembersSuffix, boolean isUserJoining, boolean roomNameFound){
    StringBuilder text = new StringBuilder();
    if(isUserJoining) {
      if(roomNameFound) {
        text.append("You have joined ").append(roomName).append(".\n");
      } else {
        text.append("You have joined the conversation.\n");
      }
    }
    text.append("Here are the members of this group: ");
    text.append(roomMembersSuffix.stream().map(s -> "firstName" + s + " lastName" + s ).collect(Collectors.joining(", ")));
    text.append(".");
    return text.toString();
  }

  private RoomMembersIdentifiersResponse generateRoomMembersIdentifiers(List<String> advisorsSuffix, List<String> emp1UsersSuffix, List<String> emp2UsersSuffix){
    List<RoomMemberIdentifier> roomMemberIdentifiers = new ArrayList<>();
    for(String suffix : advisorsSuffix){
      roomMemberIdentifiers.add(new RoomMemberIdentifier().federatedUser(false).symphonyId(symphonyId(suffix, CLIENT_POD_ID)).firstName("firstName" + suffix).lastName("lastName" + suffix).emp(null));
    }
    for(String suffix : emp1UsersSuffix){
      roomMemberIdentifiers.add(new RoomMemberIdentifier().federatedUser(true).symphonyId(symphonyId(suffix, FEDERATION_POD_ID)).firstName("firstName" + suffix).lastName("lastName" + suffix).emp("emp1"));
    }
    for(String suffix : emp2UsersSuffix){
      roomMemberIdentifiers.add(new RoomMemberIdentifier().federatedUser(true).symphonyId(symphonyId(suffix, FEDERATION_POD_ID)).firstName("firstName" + suffix).lastName("lastName" + suffix).emp("emp2"));
    }
    return new RoomMembersIdentifiersResponse().members(roomMemberIdentifiers);
  }

}
