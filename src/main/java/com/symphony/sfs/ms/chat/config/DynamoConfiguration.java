package com.symphony.sfs.ms.chat.config;

import com.symphony.sfs.ms.starter.config.BaseDynamoConfiguration;
import com.symphony.sfs.ms.starter.config.properties.AwsDynamoConfiguration;
import com.symphony.sfs.ms.starter.dynamo.schema.DynamoSchema;
import com.symphony.sfs.ms.starter.dynamo.schema.GlobalSecondaryKeyDefinition;
import com.symphony.sfs.ms.starter.dynamo.schema.PrimaryKeyDefinition;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class DynamoConfiguration extends BaseDynamoConfiguration {
  private final AwsDynamoConfiguration awsDynamoConfiguration;

  public DynamoConfiguration(AwsDynamoConfiguration awsDynamoConfiguration) {
    this.awsDynamoConfiguration = awsDynamoConfiguration;
  }

  @Override
  public DynamoSchema getDynamoSchema() {
    Pair<String, GlobalSecondaryKeyDefinition> standardGSI = GlobalSecondaryKeyDefinition.standard();
    Pair<String, GlobalSecondaryKeyDefinition> invertedPrimaryKeyGSI = PrimaryKeyDefinition.swapped();

    // inject the database schema for your microservice
    return DynamoSchema.builder()
      .tableName(awsDynamoConfiguration.getDynamodb().getTableName())
      .primaryKey(PrimaryKeyDefinition.standard())
      .secondaryKeys(Map.ofEntries(
        Map.entry(standardGSI.getKey(), standardGSI.getValue()),
        Map.entry(invertedPrimaryKeyGSI.getKey(), invertedPrimaryKeyGSI.getValue())
      ))
      .build();
  }
}
