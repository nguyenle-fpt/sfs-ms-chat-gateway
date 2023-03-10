package com.symphony.sfs.ms.chat.service.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.admin.generated.model.EmpEntity;
import com.symphony.sfs.ms.chat.generated.model.EmpNotFoundProblem;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.service.EmpMicroserviceResolver;
import com.symphony.sfs.ms.chat.service.EmpSchemaService;
import com.symphony.sfs.ms.chat.service.JwtTokenGenerator;
import com.symphony.sfs.ms.chat.util.ChannelMemberUtils;
import com.symphony.sfs.ms.emp.EmpMicroserviceClient;
import com.symphony.sfs.ms.emp.generated.model.Attachment;
import com.symphony.sfs.ms.emp.generated.model.ChannelIdentifier;
import com.symphony.sfs.ms.emp.generated.model.ChannelMember;
import com.symphony.sfs.ms.emp.generated.model.DeleteChannelsRequest;
import com.symphony.sfs.ms.emp.generated.model.DeleteChannelsResponse;
import com.symphony.sfs.ms.emp.generated.model.RoomMemberResponse;
import com.symphony.sfs.ms.emp.generated.model.SendMessageRequest;
import com.symphony.sfs.ms.emp.generated.model.SendMessageResponse;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageRequest;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageResponse;
import com.symphony.sfs.ms.emp.generated.model.SendmessagerequestInlineMessage;
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
  private final EmpSchemaService empSchemaService;

  @Override
  public Optional<SendMessageResponse> sendMessage(String emp, String streamId, String messageId, IUser fromSymphonyUser, List<FederatedAccount> toFederatedAccounts, Long timestamp, String message, String disclaimer, List<Attachment> attachments, SendmessagerequestInlineMessage inlineMessage, String jsonData) {
    EmpMicroserviceClient client = new EmpMicroserviceClient(empMicroserviceResolver.getEmpMicroserviceBaseUri(emp), webClient, objectMapper);
    SendMessageRequest request = new SendMessageRequest()
      .streamId(streamId)
      .messageId(messageId)
      .channelMembers(ChannelMemberUtils.toChannelMembers(toFederatedAccounts, fromSymphonyUser.getId().toString(), Collections.singletonList(fromSymphonyUser), empSchemaService.getEmpDefinition(emp).orElseThrow(EmpNotFoundProblem::new)))
      .fromSymphonyUserId(fromSymphonyUser.getId().toString())
      .timestamp(timestamp)
      .text(message)
      .disclaimer(disclaimer)
      .attachments(attachments)
      .inlineMessage(inlineMessage)
      .jsonData(jsonData);

    // TODO async result too?
    client.getMessagingApi().getApiClient().setSfsAuthentication(jwtTokenGenerator.generateMicroserviceToken());

    return client.getMessagingApi().sendMessage(request);
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
  public void deleteAccountOrFail(String emp, String symphonyId, String phoneNumber, String tenantId) {
    EmpMicroserviceClient client = new EmpMicroserviceClient(empMicroserviceResolver.getEmpMicroserviceBaseUri(emp), webClient, objectMapper);
    client.getUserApi().getApiClient().setSfsAuthentication(jwtTokenGenerator.generateMicroserviceToken());
    client.getUserApi().deleteUserOrFail(symphonyId, phoneNumber, tenantId);
  }

  @Override
  public Optional<UpdateUserResponse> updateAccountOrFail(String emp, String symphonyId, String phoneNumber, String tenantId, String firstName, String lastName, String companyName, String preferredLanguage) {
    EmpMicroserviceClient client = new EmpMicroserviceClient(empMicroserviceResolver.getEmpMicroserviceBaseUri(emp), webClient, objectMapper);

    UpdateUserRequest request = new UpdateUserRequest()
      .firstName(firstName)
      .lastName(lastName)
      .companyName(companyName)
      .preferredLanguage(preferredLanguage);

    client.getUserApi().getApiClient().setSfsAuthentication(jwtTokenGenerator.generateMicroserviceToken());
    return client.getUserApi().updateUserOrFail(symphonyId, phoneNumber, tenantId, request);
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
}
