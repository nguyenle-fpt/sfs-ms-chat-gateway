package com.symphony.sfs.ms.chat;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.ResourceInUseException;
import com.symphony.sfs.ms.chat.config.DynamoConfiguration;
import com.symphony.sfs.ms.starter.config.properties.AwsDynamoConfiguration;
import com.symphony.sfs.ms.starter.dynamo.exception.InvalidSchemaException;
import com.symphony.sfs.ms.starter.dynamo.schema.DynamoSchema;
import com.symphony.sfs.ms.starter.dynamo.schema.DynamoSchemaProvisionner;
import com.symphony.sfs.ms.starter.dynamo.schema.DynamoSchemaValidator;
import com.symphony.sfs.ms.starter.symphony.SymphonyDynamoSchema;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

@Slf4j
public class InitSfsChatGatewayDB {
  public static void main(String[] args) throws Exception {
    ArgumentParser parser = ArgumentParsers.newFor(InitSfsChatGatewayDB.class.getSimpleName()).build()
      .defaultHelp(true)
      .description("Initialize a DynamoDB table for sfs-ms-chat-gateway.");
    parser.addArgument("-e", "--endpoint")
      .setDefault("http://localhost:4569")
      .help("AWS dynamo db endpoint");
    parser.addArgument("-r", "--region")
      .setDefault("us-east-1")
      .help("AWS signin region");
    parser.addArgument("-v", "--validate")
      .setDefault(false)
      .help("Validate the table structure instead of creating the table");

    try {
      Namespace ns = parser.parseArgs(args);
      if (ns.getBoolean("validate")) {
        new InitSfsChatGatewayDB().validateDynamo(ns.getString("endpoint"), ns.getString("region"));
      } else {
        new InitSfsChatGatewayDB().initDynamo(ns.getString("endpoint"), ns.getString("region"));
      }
    } catch (ArgumentParserException e) {
      parser.handleError(e);
      System.exit(1);
    }
  }

  private void initDynamo(String endpoint, String region) {
    AwsDynamoConfiguration awsDynamoConfiguration = new AwsDynamoConfiguration();
    awsDynamoConfiguration.getDynamodb().setEndpoint(endpoint);
    awsDynamoConfiguration.getDynamodb().setSigninRegion(region);
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

  private void validateDynamo(String endpoint, String region) throws InvalidSchemaException {
    AwsDynamoConfiguration awsDynamoConfiguration = new AwsDynamoConfiguration();
    awsDynamoConfiguration.getDynamodb().setEndpoint(endpoint);
    awsDynamoConfiguration.getDynamodb().setSigninRegion(region);
    awsDynamoConfiguration.getDynamodb().setTableName("sfs-ms-chat-gateway");

    DynamoConfiguration dynamoConfiguration = new DynamoConfiguration(awsDynamoConfiguration);
    DynamoSchema dynamoSchema = dynamoConfiguration.getDynamoSchema();
    AmazonDynamoDB amazonDynamoDB = dynamoConfiguration.amazonDynamoDB(dynamoConfiguration.awsCredentialsProvider(awsDynamoConfiguration), awsDynamoConfiguration);

    try {
      new SymphonyDynamoSchema().validate(amazonDynamoDB);
    } catch (ResourceInUseException e) {
      LOG.error("Table {} already exists", new SymphonyDynamoSchema().getTableName());
    }

    new DynamoSchemaValidator(dynamoSchema).validate(amazonDynamoDB);
  }
}
