package com.symphony.sfs.ms.chat.service;

import com.symphony.sfs.ms.starter.modelGeneration.DynamoKey;
import com.symphony.sfs.ms.starter.modelGeneration.DynamoModel;
import com.symphony.sfs.ms.starter.modelGeneration.ModelGenerationService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class ModelGenerationTest {

  private ModelService modelService;

  @BeforeEach
  public void setUp() {
    modelService = new ModelService(new ModelGenerationService());
  }

  @Test
  public void modelTest() {
    Map<String, DynamoModel> models = modelService.generateModelDescription();

    // Check Channel
    Assertions.assertTrue(models.containsKey("Channel"));
    checkChannel(models.get("Channel"));

    // Check FederatedAccount
    Assertions.assertTrue(models.containsKey("FederatedAccount"));
    checkFederatedAccount(models.get("FederatedAccount"));
  }

  private void checkChannel(DynamoModel model) {
    // Check primary key
    checkPrimaryKey(model.getPrimaryKey(), "sfs:chat:CHANNEL#<federatedUserId>", "sfs:chat:CHANNEL#<advisorSymphonyId>#<emp>");

    // Check global secondary indexes
    List<DynamoKey> gsis = model.getGlobalSecondaryIndexes();
    Assertions.assertEquals(1, gsis.size());
    checkGlobalSecondaryIndex(gsis.get(0), "sfs:chat:CHANNEL#<streamId>", "sfs:chat:CHANNEL");

    // Check attributes
    Assertions.assertEquals(4, model.getAttributes().size());
    checkAttributes(model.getAttributes(), "streamId", "advisorSymphonyId", "federatedUserId", "emp");
  }

  private void checkFederatedAccount(DynamoModel model) {
    // Check primary key
    checkPrimaryKey(model.getPrimaryKey(), "sfs:chat:FEDERATED_ACCOUNT#<federatedUserId>", "sfs:chat:FEDERATED_ACCOUNT#<emp>");

    // Check global secondary indexes
    List<DynamoKey> gsis = model.getGlobalSecondaryIndexes();
    Assertions.assertEquals(1, gsis.size());
    checkGlobalSecondaryIndex(gsis.get(0), "sfs:chat:FEDERATED_ACCOUNT#<symphonyId>", "sfs:chat:FEDERATED_ACCOUNT");

    // Check attributes
    Assertions.assertEquals(11, model.getAttributes().size());
    checkAttributes(model.getAttributes(), "federatedUserId", "firstName", "lastName", "companyName", "emailAddress", "phoneNumber", "emp", "symphonyUserId", "symphonyUsername", "sessionToken", "kmToken");
  }

  private void checkPrimaryKey(DynamoKey primaryKey, String pk, String sk) {
    Assertions.assertEquals(pk, primaryKey.getPk());
    Assertions.assertEquals(sk, primaryKey.getSk());
  }

  private void checkGlobalSecondaryIndex(DynamoKey gsi, String pk, String sk) {
    Assertions.assertEquals(pk, gsi.getPk());
    Assertions.assertEquals(sk, gsi.getSk());
  }

  private void checkAttributes(List<String> actualAttributes, String... expectedAttributes) {
    int index = 0;
    for (String attr : actualAttributes) {
      Assertions.assertEquals(expectedAttributes[index], attr);
      index++;
    }
  }
}
