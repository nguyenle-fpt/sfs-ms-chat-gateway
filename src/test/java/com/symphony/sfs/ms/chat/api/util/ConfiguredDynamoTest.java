package com.symphony.sfs.ms.chat.api.util;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.symphony.sfs.ms.chat.config.DynamoConfiguration;
import com.symphony.sfs.ms.starter.config.properties.AwsDynamoConfiguration;
import com.symphony.sfs.ms.starter.dynamo.schema.DynamoSchema;
import com.symphony.sfs.ms.starter.testing.DynamoTest;

import static com.symphony.sfs.ms.starter.testing.DynamoTestUtils.createTable;
import static com.symphony.sfs.ms.starter.testing.DynamoTestUtils.deleteTable;
import static com.symphony.sfs.ms.starter.testing.DynamoTestUtils.tableExists;

public interface ConfiguredDynamoTest extends DynamoTest {
  default DynamoConfiguration getDynamoConfiguration() {
    AwsDynamoConfiguration awsDynamoConfiguration = new AwsDynamoConfiguration();
    awsDynamoConfiguration.getDynamodb().setTableName("sfs-ms-chat-gateway-test");
    return new DynamoConfiguration(awsDynamoConfiguration);
  }

  default DynamoSchema getDynamoSchema() {
    return getDynamoConfiguration().getDynamoSchema();
  }

  default DynamoConfiguration provisionTestTable(AmazonDynamoDB db) {
    DynamoConfiguration config = getDynamoConfiguration();
    createTable(db, config.getDynamoSchema());
    return config;
  }

  default void deleteTestTable(AmazonDynamoDB db) {
    if (tableExists(db, getDynamoSchema().getTableName())) {
      deleteTable(db, getDynamoSchema().getTableName());
    }
  }
}
