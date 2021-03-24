package com.symphony.sfs.ms.chat.service.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.service.EmpMicroserviceResolver;
import com.symphony.sfs.ms.chat.service.JwtTokenGenerator;
import com.symphony.sfs.ms.emp.EmpMicroserviceClient;
import com.symphony.sfs.ms.emp.generated.model.AsyncResult;
import com.symphony.sfs.ms.emp.generated.model.Attachment;
import com.symphony.sfs.ms.emp.generated.model.ChannelIdentifier;
import com.symphony.sfs.ms.emp.generated.model.ChannelMember;
import com.symphony.sfs.ms.emp.generated.model.CreateChannelRequest;
import com.symphony.sfs.ms.emp.generated.model.DeleteChannelsRequest;
import com.symphony.sfs.ms.emp.generated.model.DeleteChannelsResponse;
import com.symphony.sfs.ms.emp.generated.model.OperationIdBySymId;
import com.symphony.sfs.ms.emp.generated.model.RoomMemberResponse;
import com.symphony.sfs.ms.emp.generated.model.SendMessageRequest;
import com.symphony.sfs.ms.emp.generated.model.SendMessageResponse;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageRequest;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageResponse;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageToChannelsRequest;
import com.symphony.sfs.ms.emp.generated.model.UpdateUserRequest;
import com.symphony.sfs.ms.emp.generated.model.UpdateUserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class DefaultEmpClient implements EmpClient {

  private final WebClient webClient;
  private final ObjectMapper objectMapper;
  private final EmpMicroserviceResolver empMicroserviceResolver;
  private final JwtTokenGenerator jwtTokenGenerator;

  @Override
  public Optional<String> createChannel(String emp, String streamId, List<FederatedAccount> federatedUsers, String initiatorUserId, List<IUser> symphonyUsers) {
    EmpMicroserviceClient client = new EmpMicroserviceClient(empMicroserviceResolver.getEmpMicroserviceBaseUri(emp), webClient, objectMapper);

    CreateChannelRequest request = new CreateChannelRequest()
      .streamId(streamId)
      .members(toChannelMembers(federatedUsers, initiatorUserId, symphonyUsers));

    client.getChannelApi().getApiClient().setSfsAuthentication(jwtTokenGenerator.generateMicroserviceToken());
    return client.getChannelApi().createChannelOrFail(request).map(AsyncResult::getOperationId);
  }

  @Override
  public Optional<List<OperationIdBySymId>> sendMessage(String emp, String streamId, String messageId, IUser fromSymphonyUser, List<FederatedAccount> toFederatedAccounts, Long timestamp, String message, String disclaimer, List<Attachment> attachments) {
    EmpMicroserviceClient client = new EmpMicroserviceClient(empMicroserviceResolver.getEmpMicroserviceBaseUri(emp), webClient, objectMapper);

    SendMessageRequest request = new SendMessageRequest()
      .streamId(streamId)
      .messageId(messageId)
      .channelMembers(toChannelMembers(toFederatedAccounts, fromSymphonyUser.getId().toString(), Collections.singletonList(fromSymphonyUser)))
      .fromSymphonyUserId(fromSymphonyUser.getId().toString())
      .timestamp(timestamp)
      .text(message)
      .disclaimer(disclaimer)
      .attachments(attachments);

    // TODO async result too?
    client.getMessagingApi().getApiClient().setSfsAuthentication(jwtTokenGenerator.generateMicroserviceToken());
    return client.getMessagingApi().sendMessage(request).map(SendMessageResponse::getOperationIds);
  }

  @Override
  public Optional<String> sendSystemMessage(String emp, String streamId, String symphonyId, Long timestamp, String message, SendSystemMessageRequest.TypeEnum type) {
    EmpMicroserviceClient client = new EmpMicroserviceClient(empMicroserviceResolver.getEmpMicroserviceBaseUri(emp), webClient, objectMapper);

    SendSystemMessageRequest request = new SendSystemMessageRequest()
      .streamId(streamId)
      .symphonyId(symphonyId)
      .timestamp(timestamp)
      .text(message)
      .type(type);

    // TODO async result too?
    client.getMessagingApi().getApiClient().setSfsAuthentication(jwtTokenGenerator.generateMicroserviceToken());
    return client.getMessagingApi().sendSystemMessage(request).map(SendSystemMessageResponse::getOperationId);
  }

  @Override
  public void sendSystemMessageToChannels(String emp, List<ChannelIdentifier> channels, String message, boolean shouldBeResent) {
    EmpMicroserviceClient client = new EmpMicroserviceClient(empMicroserviceResolver.getEmpMicroserviceBaseUri(emp), webClient, objectMapper);

    SendSystemMessageToChannelsRequest request = new SendSystemMessageToChannelsRequest()
      .channels(channels)
      .text(message)
      .shouldBeResent(shouldBeResent);

    client.getMessagingApi().getApiClient().setSfsAuthentication(jwtTokenGenerator.generateMicroserviceToken());
    client.getMessagingApi().sendSystemMessageToChannels(request);
  }

  @Override
  public void deleteAccountOrFail(String emp, String symphonyId, String emailAddress, String phoneNumber) {
    EmpMicroserviceClient client = new EmpMicroserviceClient(empMicroserviceResolver.getEmpMicroserviceBaseUri(emp), webClient, objectMapper);
    client.getUserApi().getApiClient().setSfsAuthentication(jwtTokenGenerator.generateMicroserviceToken());
    client.getUserApi().deleteUserOrFail(symphonyId, emailAddress, phoneNumber);
  }

  @Override
  public Optional<UpdateUserResponse> updateAccountOrFail(String emp, String symphonyId, String emailAddress, String phoneNumber, String firstName, String lastName, String companyName) {
    EmpMicroserviceClient client = new EmpMicroserviceClient(empMicroserviceResolver.getEmpMicroserviceBaseUri(emp), webClient, objectMapper);

    UpdateUserRequest request = new UpdateUserRequest()
      .firstName(firstName)
      .lastName(lastName)
      .companyName(companyName);

    client.getUserApi().getApiClient().setSfsAuthentication(jwtTokenGenerator.generateMicroserviceToken());
    return client.getUserApi().updateUserOrFail(symphonyId, emailAddress, phoneNumber, request);
  }

  @Override
  public Optional<DeleteChannelsResponse> deleteChannels(List<ChannelIdentifier> deleteChannelRequests, String emp) {
    EmpMicroserviceClient client = new EmpMicroserviceClient(empMicroserviceResolver.getEmpMicroserviceBaseUri(emp), webClient, objectMapper);
    client.getUserApi().getApiClient().setSfsAuthentication(jwtTokenGenerator.generateMicroserviceToken());
    return client.getChannelApi().deleteChannelsOrFail(new DeleteChannelsRequest().channels(deleteChannelRequests));
  }

  @Override
  public Optional<RoomMemberResponse> addRoomMemberOrFail(String streamId, String emp, com.symphony.sfs.ms.emp.generated.model.RoomMemberRequest empRoomMemberRequest) {
    EmpMicroserviceClient client = new EmpMicroserviceClient(empMicroserviceResolver.getEmpMicroserviceBaseUri(emp), webClient, objectMapper);

    client.getRoomApi().getApiClient().setSfsAuthentication(jwtTokenGenerator.generateMicroserviceToken());

    return client.getRoomApi().addRoomMemberOrFail(streamId, empRoomMemberRequest);
  }

  private List<ChannelMember> toChannelMembers(List<FederatedAccount> federatedUsers, String initiatorUserId, List<IUser> symphonyUsers) {
    List<ChannelMember> members = new ArrayList<>();
    federatedUsers.forEach(account -> members.add(new ChannelMember()
      .emailAddress(account.getEmailAddress())
      .phoneNumber(account.getPhoneNumber())
      .firstName(account.getFirstName())
      .lastName(account.getLastName())
      .companyName(account.getCompanyName())
      .federatedUserId(account.getFederatedUserId())
      .symphonyId(account.getSymphonyUserId())
      .isFederatedUser(true)
      .isInitiator(initiatorUserId.equals(account.getSymphonyUserId()))
    ));
    symphonyUsers.forEach(user -> members.add(new ChannelMember()
      .symphonyId(user.getId().toString())
      .firstName(user.getFirstName())
      .lastName(user.getSurname())
      .displayName(user.getPrettyName())
      .companyName(Objects.requireNonNullElse(user.getCompany(), "Guest"))
      .isFederatedUser(false)
      .isInitiator(initiatorUserId.equals(user.getId().toString()))
    ));

    return members;
  }
}
