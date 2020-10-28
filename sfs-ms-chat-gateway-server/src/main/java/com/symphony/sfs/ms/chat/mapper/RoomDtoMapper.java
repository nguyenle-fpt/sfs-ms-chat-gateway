package com.symphony.sfs.ms.chat.mapper;

import com.symphony.sfs.ms.chat.generated.model.RoomRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface RoomDtoMapper {

  RoomDtoMapper MAPPER = Mappers.getMapper(RoomDtoMapper.class);

  RoomResponse roomRequestToRoomResponse(RoomRequest request);

}
