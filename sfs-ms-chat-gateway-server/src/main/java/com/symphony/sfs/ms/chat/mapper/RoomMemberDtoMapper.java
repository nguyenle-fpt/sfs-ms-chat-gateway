package com.symphony.sfs.ms.chat.mapper;

import com.symphony.sfs.ms.chat.generated.model.RoomMemberRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomMemberResponse;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import model.UserInfo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface RoomMemberDtoMapper {

  RoomMemberDtoMapper MAPPER = Mappers.getMapper(RoomMemberDtoMapper.class);

  RoomMemberResponse roomMemberRequestToRoomMemberResponse(RoomMemberRequest request);
  @Mapping(source= "onboarderInfo.firstName", target = "onboarderFirstName")
  @Mapping(source= "onboarderInfo.lastName", target = "onboarderLastName")
  @Mapping(source= "onboarderInfo.displayName", target = "onboarderDisplayName")
  @Mapping(source= "onboarderInfo.company", target = "onboarderCompanyName")
  @Mapping(source= "federatedAccount.companyName", target = "companyName")
  @Mapping(source= "federatedAccount.firstName", target = "firstName")
  @Mapping(source= "federatedAccount.lastName", target = "lastName")
  @Mapping(source= "chatRoomMemberRequest.advisorPhoneNumber", target = "advisorPhoneNumber")

  com.symphony.sfs.ms.emp.generated.model.RoomMemberRequest toEmpRoomMemberRequest(RoomMemberRequest chatRoomMemberRequest, FederatedAccount federatedAccount, UserInfo onboarderInfo);

  com.symphony.sfs.ms.emp.generated.model.RoomMemberResponse empRoomMemberRequestToEmpRoomMemberResponse(com.symphony.sfs.ms.emp.generated.model.RoomMemberRequest empRoomMemberRequest);
}
