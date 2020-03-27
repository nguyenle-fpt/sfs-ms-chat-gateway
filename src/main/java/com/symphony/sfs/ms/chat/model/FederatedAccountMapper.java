package com.symphony.sfs.ms.chat.model;

import com.symphony.sfs.ms.starter.dynamo.util.AttributeUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FederatedAccountMapper {

  public static String federatedAccountPk(String federatedUserId) {
    return AttributeUtils.buildStandardHierarchicalKey(FederatedAccount.TYPE, federatedUserId);
  }

  public static String federatedAccountSk(String emp) {
    return emp;
  }

  public static String federatedAccountGsi1Pk(String symphonyUserId) {
    return symphonyUserId;
  }

  public static String federatedAccountGsi1Sk() {
    return FederatedAccount.TYPE;
  }

}
