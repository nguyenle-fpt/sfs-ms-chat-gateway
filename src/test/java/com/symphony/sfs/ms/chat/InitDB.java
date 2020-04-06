package com.symphony.sfs.ms.chat;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.ResourceInUseException;
import com.symphony.sfs.ms.chat.config.DynamoConfiguration;
import com.symphony.sfs.ms.starter.config.properties.AwsDynamoConfiguration;
import com.symphony.sfs.ms.starter.dynamo.schema.DynamoSchema;
import com.symphony.sfs.ms.starter.dynamo.schema.DynamoSchemaProvisionner;
import com.symphony.sfs.ms.starter.symphony.SymphonyDynamoSchema;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InitDB {

  public static void main(String[] args) {

    AwsDynamoConfiguration awsDynamoConfiguration = new AwsDynamoConfiguration();
    awsDynamoConfiguration.getDynamodb().setEndpoint("http://localhost:4569");
    awsDynamoConfiguration.getDynamodb().setSigninRegion("us-east-1");
    awsDynamoConfiguration.getDynamodb().setTableName("sfs-ms-chat-gateway");

    DynamoConfiguration dynamoConfiguration = new DynamoConfiguration(awsDynamoConfiguration);
    DynamoSchema dynamoSchema = dynamoConfiguration.getDynamoSchema();
    AmazonDynamoDB amazonDynamoDB = dynamoConfiguration.amazonDynamoDB(dynamoConfiguration.awsCredentialsProvider(awsDynamoConfiguration), awsDynamoConfiguration);

    try {
      new SymphonyDynamoSchema().provision(amazonDynamoDB);
    } catch (ResourceInUseException e) {
      LOG.error("Table {} already exists", new SymphonyDynamoSchema().getTableName());
    }

    new DynamoSchemaProvisionner(dynamoSchema).provision(amazonDynamoDB);
  }

}
