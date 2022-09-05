package com.symphony.sfs.ms.chat.util;

import com.symphony.oss.models.chat.canon.UserEntity;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.oss.models.chat.canon.facade.User;
import com.symphony.sfs.ms.admin.generated.model.EmpEntity;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.emp.generated.model.ChannelMember;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 *
 */
public class ChannelMemberUtils {
  public static List<ChannelMember> toChannelMembers(List<FederatedAccount> federatedUsers, String initiatorUserId, List<IUser> symphonyUsers, EmpEntity empEntity) {
    List<ChannelMember> members = new ArrayList<>();
    federatedUsers.forEach(account -> members.add(new ChannelMember()
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
      // Add "displayName" here because it could happen that the message is sent by another Service account (EMP user), hence without firs name nor last name
      .displayName(getDisplayName(user.getPrettyName(), empEntity))
      .companyName(Objects.requireNonNullElse(user.getCompany(), "Guest"))
      .isFederatedUser(false)
      .isInitiator(initiatorUserId.equals(user.getId().toString()))
    ));

    return members;
  }

  /*
    https://perzoinc.atlassian.net/browse/CES-9198
    In case the message sender is EMP user (then becomes message sent by EMP Pod Service Account at this step to Symphony room),
    we want to eliminate the "suffix" part in its display name (prettyName)
   */
  private static String getDisplayName(String prettyName, EmpEntity empEntity) {
    int index = prettyName.indexOf(" [" + empEntity.getServiceAccountSuffix() + "]");
    return index > -1 ? prettyName.substring(0, index) : prettyName;
  }
}
