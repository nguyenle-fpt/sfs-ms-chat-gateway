package com.symphony.sfs.ms.chat.mapper;

import com.symphony.sfs.ms.chat.generated.model.RoomMemberRequest;
import com.symphony.sfs.ms.chat.generated.model.RoomMemberResponse;
import com.symphony.sfs.ms.chat.generated.model.UpdateAccountResponse;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.emp.generated.model.UpdateUserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface UpdateAccountResponseDtoMapper {

  UpdateAccountResponseDtoMapper MAPPER = Mappers.getMapper(UpdateAccountResponseDtoMapper.class);

  UpdateAccountResponse federatedAccountToUpdateAccountResponse(FederatedAccount federatedAccount);

}
