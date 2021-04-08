package com.symphony.sfs.ms.chat.config;

import com.symphony.sfs.ms.starter.config.BaseDynamoConfiguration;
import com.symphony.sfs.ms.starter.config.StaticDynamoConfiguration;
import com.symphony.sfs.ms.starter.config.properties.AwsDynamoConfiguration;
import com.symphony.sfs.ms.starter.dynamo.schema.DynamoSchema;
import com.symphony.sfs.ms.starter.dynamo.schema.GlobalSecondaryKeyDefinition;
import com.symphony.sfs.ms.starter.dynamo.schema.PrimaryKeyDefinition;
import org.springframework.context.annotation.Configuration;

import javax.lang.model.element.TypeParameterElement;
import java.util.Map;

@Configuration
public class DynamoConfiguration extends BaseDynamoConfiguration {
  private final AwsDynamoConfiguration awsDynamoConfiguration;

  public static final String TYPE_PREFIX = "sfs:chat:";

  static {
    // Handle Repository directly implemented in chassis
    // com.symphony.sfs.ms.starter.i18n.MessageEntityRepository for example
    StaticDynamoConfiguration.setTypePrefix(TYPE_PREFIX);
  }

  public static String GSI1_IDX = "gsi1";
  public static String GSI1_PK = "gsi1pk";
  public static String GSI1_SK = "gsi1sk";

  public DynamoConfiguration(AwsDynamoConfiguration awsDynamoConfiguration) {
    this.awsDynamoConfiguration = awsDynamoConfiguration;
  }

  @Override
  public DynamoSchema getDynamoSchema() {
    return DynamoSchema.builder()
      .tableName(awsDynamoConfiguration.getDynamodb().getTableName())
      .primaryKey(PrimaryKeyDefinition.standard())
      .secondaryKeys(Map.of(
        GSI1_IDX, new GlobalSecondaryKeyDefinition(GSI1_PK, GSI1_SK)
      ))
      .build();
  }
}
