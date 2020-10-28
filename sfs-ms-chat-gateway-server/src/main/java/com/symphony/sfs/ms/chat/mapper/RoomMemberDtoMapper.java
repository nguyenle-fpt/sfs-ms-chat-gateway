package com.symphony.sfs.ms.chat.mapper;

import com.symphony.sfs.ms.chat.generated.model.RoomMemberRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomMemberResponse;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface RoomMemberDtoMapper {

  RoomMemberDtoMapper MAPPER = Mappers.getMapper(RoomMemberDtoMapper.class);

  RoomMemberResponse roomMemberRequestToRoomMemberResponse(RoomMemberRequest request);

  com.symphony.sfs.ms.emp.generated.model.RoomMemberRequest toEmpRoomMemberRequest(RoomMemberRequest chatRoomMemberRequest, FederatedAccount federatedAccount);

  com.symphony.sfs.ms.emp.generated.model.RoomMemberResponse empRoomMemberRequestToEmpRoomMemberResponse(com.symphony.sfs.ms.emp.generated.model.RoomMemberRequest empRoomMemberRequest);
}
