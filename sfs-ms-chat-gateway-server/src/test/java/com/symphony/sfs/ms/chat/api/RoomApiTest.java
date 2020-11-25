package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.chat.api.util.AbstractIntegrationTest;
import com.symphony.sfs.ms.chat.generated.model.RoomMemberRemoveRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomMemberRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomMemberResponse;
import com.symphony.sfs.ms.chat.generated.model.RoomRemoveRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomResponse;
import com.symphony.sfs.ms.chat.mapper.RoomMemberDtoMapper;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.emp.generated.model.DeleteChannelRequest;
import com.symphony.sfs.ms.starter.config.ExceptionHandling;
import com.symphony.sfs.ms.starter.security.SessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyResponse;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyRoom;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyRoomAttributes;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyRoomSystemInfo;
import com.symphony.sfs.ms.starter.util.UserIdUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.symphony.sfs.ms.chat.generated.api.RoomApi.ADDROOMMEMBER_ENDPOINT;
import static com.symphony.sfs.ms.chat.generated.api.RoomApi.CREATEROOM_ENDPOINT;
import static com.symphony.sfs.ms.chat.generated.api.RoomApi.DELETEROOM_ENDPOINT;
import static com.symphony.sfs.ms.chat.generated.api.RoomApi.REMOVEMEMBER_ENDPOINT;
import static com.symphony.sfs.ms.chat.util.HttpRequestUtils.postRequestFail;
import static com.symphony.sfs.ms.starter.testing.MockMvcUtils.configuredGiven;
import static com.symphony.sfs.ms.starter.testing.MockitoUtils.once;
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

  /////////////////////
  // add room member //
  /////////////////////
  @Test
  public void addRoomMember_SymphonyFail() {
    createRoom_OK();

    String podUrl = podConfiguration.getUrl();
    doReturn(Optional.empty()).when(streamService).addRoomMember(eq(podUrl), any(SessionSupplier.class), anyString(), anyString());

    RoomMemberRequest roomMemberRequest = new RoomMemberRequest().clientPodId(CLIENT_POD_ID).symphonyId(symphonyId("21", CLIENT_POD_ID)).federatedUser(false);
    addRoomMemberFail(ROOM_STREAM_ID, roomMemberRequest, com.symphony.sfs.ms.chat.generated.model.AddRoomMemberFailedProblem.class.getName(), HttpStatus.INTERNAL_SERVER_ERROR);

  }

  @Test
  public void addRoomMember_FederatedAccountNotFound() {

    String podUrl = podConfiguration.getUrl();
    doReturn(Optional.of(new SymphonyResponse())).when(streamService).addRoomMember(eq(podUrl), any(SessionSupplier.class), anyString(), anyString());

    SymphonyResponse symphonyResponse = SymphonyResponse.builder().format("TEXT").message("Member added").build();
    doReturn(Optional.of(symphonyResponse)).when(streamService).addRoomMember(eq(podUrl), any(SessionSupplier.class), eq(ROOM_STREAM_ID), eq(symphonyId("11", FEDERATION_POD_ID)));

    RoomMemberRequest roomMemberRequest = new RoomMemberRequest().clientPodId(CLIENT_POD_ID).symphonyId(symphonyId("11", FEDERATION_POD_ID)).federatedUser(true);
    addRoomMemberFail(ROOM_STREAM_ID, roomMemberRequest, com.symphony.sfs.ms.chat.generated.model.FederatedAccountNotFoundProblem.class.getName(), HttpStatus.BAD_REQUEST);

  }

  @Test
  public void addRoomMember_OK() {

    createRoom_OK();

    String podUrl = podConfiguration.getUrl();

    SymphonyResponse symphonyResponse = SymphonyResponse.builder().format("TEXT").message("Member added").build();
    doReturn(Optional.of(symphonyResponse)).when(streamService).addRoomMember(eq(podUrl), any(SessionSupplier.class), eq(ROOM_STREAM_ID), eq(symphonyId("11", FEDERATION_POD_ID)));

    FederatedAccount federatedAccount = federatedAccountRepository.save(FederatedAccount.builder()
      .symphonyUserId(symphonyId("11", FEDERATION_POD_ID))
      .federatedUserId(federatedUserId("11"))
      .emp(emp("11"))
      .emailAddress(emailAddress("11"))
      .phoneNumber((phoneNumber("11")))
      .build());
    RoomMemberRequest roomMemberRequest = new RoomMemberRequest().clientPodId(CLIENT_POD_ID).symphonyId(symphonyId("11", FEDERATION_POD_ID)).federatedUser(true);
    RoomMemberResponse roomMemberResponse = addRoomMember(ROOM_STREAM_ID, roomMemberRequest);


    assertEquals(ROOM_STREAM_ID, roomMemberResponse.getStreamId());
    assertEquals(symphonyId("11", FEDERATION_POD_ID), roomMemberResponse.getSymphonyId());
    assertEquals(federatedUserId("11"), roomMemberResponse.getFederatedUserId());
    assertEquals(emp("11"), roomMemberResponse.getEmp());
    assertEquals(phoneNumber("11"), roomMemberResponse.getPhoneNumber());
    assertEquals(emailAddress("11"), roomMemberResponse.getEmailAddress());

    com.symphony.sfs.ms.emp.generated.model.RoomMemberRequest empRoomMemberRequest = RoomMemberDtoMapper.MAPPER.toEmpRoomMemberRequest(roomMemberRequest, federatedAccount);
    verify(empClient, once()).addRoomMember(ROOM_STREAM_ID,  emp("11"), empRoomMemberRequest);
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

    List<DeleteChannelRequest> deleteChannelRequests = Collections.singletonList(
      new DeleteChannelRequest().streamId(streamId).symphonyId(symphonyId)
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

    List<DeleteChannelRequest> deleteChannelRequests = List.of(
      new DeleteChannelRequest().streamId(streamId).symphonyId("12346"),
      new DeleteChannelRequest().streamId(streamId).symphonyId("12347")
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

    List<DeleteChannelRequest> deleteChannelRequests = List.of(
      new DeleteChannelRequest().streamId(streamId).symphonyId("12346")
    );
    List<DeleteChannelRequest> deleteChannelRequestsSecondEmp = List.of(
      new DeleteChannelRequest().streamId(streamId).symphonyId("12347")
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

  private void createRoomFail(RoomRequest roomRequest, String problemClassName, HttpStatus httpStatus) {
    postRequestFail(roomRequest, roomApi, CREATEROOM_ENDPOINT, objectMapper, tracer, problemClassName, httpStatus);
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

  private void addRoomMemberFail(String streamId, RoomMemberRequest roomRequest, String problemClassName, HttpStatus httpStatus) {
    postRequestFail(roomRequest, roomApi, ADDROOMMEMBER_ENDPOINT, Collections.singletonList(streamId), objectMapper, tracer, problemClassName, httpStatus);
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

}
