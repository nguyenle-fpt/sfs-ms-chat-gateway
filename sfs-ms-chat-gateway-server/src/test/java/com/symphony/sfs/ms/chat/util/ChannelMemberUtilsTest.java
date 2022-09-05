package com.symphony.sfs.ms.chat.util;

import com.symphony.oss.models.chat.canon.UserEntity;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.oss.models.chat.canon.facade.User;
import com.symphony.sfs.ms.admin.generated.model.EmpEntity;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.emp.generated.model.ChannelMember;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ChannelMemberUtilsTest {
  @Test
  public void returnChannelMemberList_SenderIsAdvisor() {
    EmpEntity empEntity = new EmpEntity().name("emp").serviceAccountSuffix("EMP");
    List<ChannelMember> channelMembers = ChannelMemberUtils.toChannelMembers(List.of(newFederatedAccount("123456789")), "987654321", List.of(newNormalUser("987654321")), empEntity);
    assertEquals(List.of(
      new ChannelMember().phoneNumber("+33123456789")
        .firstName("First Name")
        .lastName("Last Name")
        .companyName("ABC")
        .federatedUserId("bf45680d-5bb9-472e-81ff-9974465a2c8d")
        .symphonyId("1234567898")
        .isFederatedUser(true)
        .isInitiator(false),
      new ChannelMember()
        .firstName("First Name 1")
        .lastName("Last Name 1")
        .displayName("Display Name 1")
        .companyName("Guest")
        .symphonyId("987654321")
        .isFederatedUser(false)
        .isInitiator(true)
    ), channelMembers);
  }

  @Test
  public void returnChannelMemberList_SenderIsEmpUser() {
    EmpEntity empEntity = new EmpEntity().name("emp").serviceAccountSuffix("EMP");
    List<ChannelMember> channelMembers = ChannelMemberUtils.toChannelMembers(List.of(newFederatedAccount("123456789")), "987654321", List.of(newServiceAccountlUser("987654321", empEntity.getServiceAccountSuffix())), empEntity);
    assertEquals(List.of(
      new ChannelMember().phoneNumber("+33123456789")
        .firstName("First Name")
        .lastName("Last Name")
        .companyName("ABC")
        .federatedUserId("bf45680d-5bb9-472e-81ff-9974465a2c8d")
        .symphonyId("1234567898")
        .isFederatedUser(true)
        .isInitiator(false),
      new ChannelMember()
        .displayName("PrettyName")
        .companyName("Company")
        .symphonyId("987654321")
        .isFederatedUser(false)
        .isInitiator(true)
    ), channelMembers);
  }

  private static FederatedAccount newFederatedAccount(String symphonyId) {
    return FederatedAccount.builder()
      .phoneNumber("+33123456789")
      .firstName("First Name")
      .lastName("Last Name")
      .companyName("ABC")
      .federatedUserId("bf45680d-5bb9-472e-81ff-9974465a2c8d")
      .symphonyUserId("1234567898")
      .build();
  }

  private static IUser newNormalUser(String symphonyId) {
    // TODO resolve firstName and lastName
    UserEntity.Builder builder = new UserEntity.Builder()
      .withId(Long.valueOf(symphonyId))
      .withFirstName("First Name 1")
      .withSurname("Last Name 1")
      .withPrettyName("Display Name 1")
      .withCompany(null);
    return new User(builder);

  }
  private static IUser newServiceAccountlUser(String symphonyId, String empSuffix) {
    // TODO resolve firstName and lastName
    UserEntity.Builder builder = new UserEntity.Builder()
      .withId(Long.valueOf(symphonyId))
      .withPrettyName(String.format("PrettyName [%s]",empSuffix))
      .withCompany("Company");
    return new User(builder);

  }
}
