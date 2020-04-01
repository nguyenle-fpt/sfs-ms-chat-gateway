package com.symphony.sfs.ms.chat.model;

import com.symphony.sfs.ms.starter.dynamo.DynamoDocument;
import com.symphony.sfs.ms.starter.dynamo.util.AttributeMap;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;


import static com.symphony.sfs.ms.chat.config.DynamoConfiguration.GSI1_IDX;
import static com.symphony.sfs.ms.chat.config.DynamoConfiguration.GSI1_PK;
import static com.symphony.sfs.ms.chat.config.DynamoConfiguration.GSI1_SK;
import static com.symphony.sfs.ms.chat.model.FederatedAccountMapper.federatedAccountGsi1Pk;
import static com.symphony.sfs.ms.chat.model.FederatedAccountMapper.federatedAccountGsi1Sk;
import static com.symphony.sfs.ms.chat.model.FederatedAccountMapper.federatedAccountPk;
import static com.symphony.sfs.ms.chat.model.FederatedAccountMapper.federatedAccountSk;
import static com.symphony.sfs.ms.starter.dynamo.DynamoConstants.PARTITION_KEY;
import static com.symphony.sfs.ms.starter.dynamo.DynamoConstants.SORT_KEY;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public class FederatedAccount extends DynamoDocument {

  public static final String TYPE = "sfs:chat:FEDERATED_ACCOUNT";

  private String federatedUserId;
  private String firstName;
  private String lastName;
  private String emailAddress;
  private String phoneNumber;
  private String emp;
  private String symphonyUserId;
  private String symphonyUsername;

  public FederatedAccount(AttributeMap attributes) {
    super(attributes);
    this.federatedUserId = attributes.getString("federatedUserId");
    this.emailAddress = attributes.getString("emailAddress");
    this.phoneNumber = attributes.getString("phoneNumber");
    this.firstName = attributes.getString("firstName");
    this.lastName = attributes.getString("lastName");
    this.emp = attributes.getString("emp");
    this.symphonyUserId = attributes.getString("symphonyUserId");
    this.symphonyUsername = attributes.getString("symphonyUsername");
  }

  @Override
  public AttributeMap toAttributeMap() {
    AttributeMap attributes = super.toAttributeMap();

    attributes.putString("federatedUserId", getFederatedUserId());
    attributes.putString("firstName", getFirstName());
    attributes.putString("lastName", getLastName());
    attributes.putString("emailAddress", getEmailAddress());
    attributes.putString("phoneNumber", getPhoneNumber());
    attributes.putString("emp", getEmp());
    attributes.putString("symphonyUserId", getSymphonyUserId());
    attributes.putString("symphonyUsername", getSymphonyUsername());

    attributes.putString(PARTITION_KEY, federatedAccountPk(getFederatedUserId()));
    attributes.putString(SORT_KEY, federatedAccountSk(getEmp()));
    attributes.putString(GSI1_PK, federatedAccountGsi1Pk(getSymphonyUserId()));
    attributes.putString(GSI1_SK, federatedAccountGsi1Sk());

    return attributes;
  }
}
