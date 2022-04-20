package com.symphony.sfs.ms.chat.mapper;

import com.symphony.sfs.ms.chat.datafeed.SBEEventMessage;
import com.symphony.sfs.ms.chat.generated.model.MessageInfoWithCustomEntities;
import com.symphony.sfs.ms.starter.symphony.stream.SymphonyInboundMessage;
import model.InboundMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MessageInfoMapper {

  @Mapping(source= "inboundMessage.user.firstName", target = "firstName")
  @Mapping(source= "inboundMessage.user.lastName", target = "lastName")
  @Mapping(source= "inboundMessage.user.displayName", target = "displayName")
  @Mapping(source= "inboundMessage.user.userId", target = "symphonyId")
  MessageInfoWithCustomEntities inboundMessageToMessageInfo(InboundMessage inboundMessage);


  @Mapping(source= "symphonyInboundMessage.user.userAttributes.firstName", target = "firstName")
  @Mapping(source= "symphonyInboundMessage.user.userAttributes.lastName", target = "lastName")
  @Mapping(source= "symphonyInboundMessage.user.userAttributes.displayName", target = "displayName")
  @Mapping(source= "symphonyInboundMessage.user.userSystemInfo.id", target = "symphonyId")
  MessageInfoWithCustomEntities symphonyInboundMessageToMessageInfo(SymphonyInboundMessage symphonyInboundMessage);


  @Mapping(source= "sbeEventMessage.text", target = "message")
  @Mapping(source= "sbeEventMessage.from.firstName", target = "firstName")
  @Mapping(source= "sbeEventMessage.from.surName", target = "lastName")
  @Mapping(source= "sbeEventMessage.from.prettyName", target = "displayName")
  @Mapping(source= "sbeEventMessage.from.id", target = "symphonyId")
  @Mapping(source= "sbeEventMessage.ingestionDate", target = "timestamp")
  MessageInfoWithCustomEntities sbeEventMessageToMessageInfo(SBEEventMessage sbeEventMessage);

}
