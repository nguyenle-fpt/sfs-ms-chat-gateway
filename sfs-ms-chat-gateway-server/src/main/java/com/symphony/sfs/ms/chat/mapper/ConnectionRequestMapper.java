package com.symphony.sfs.ms.chat.mapper;

import com.symphony.sfs.ms.chat.generated.model.ConnectionRequestResponse;
import com.symphony.sfs.ms.starter.symphony.xpod.ConnectionRequest;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface ConnectionRequestMapper {
  ConnectionRequestMapper MAPPER = Mappers.getMapper(ConnectionRequestMapper.class);

  public ConnectionRequestResponse ConnectionRequestToConnectionRequestResponse(ConnectionRequest connectionRequest);
}
