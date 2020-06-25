package com.symphony.sfs.ms.chat.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.symphony.sfs.ms.starter.dynamo.util.AttributeUtils.buildStandardHierarchicalKey;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FederatedAccountMapper {

  public static String federatedAccountPk(String federatedUserId) {
    return buildStandardHierarchicalKey(FederatedAccount.TYPE, federatedUserId);
  }

  public static String federatedAccountSk(String emp) {
    return buildStandardHierarchicalKey(FederatedAccount.TYPE, emp);
  }

  public static String federatedAccountGsi1Pk(String symphonyId) {
    return buildStandardHierarchicalKey(FederatedAccount.TYPE, symphonyId);
  }

  public static String federatedAccountGsi1Sk() {
    return FederatedAccount.TYPE;
  }

}
