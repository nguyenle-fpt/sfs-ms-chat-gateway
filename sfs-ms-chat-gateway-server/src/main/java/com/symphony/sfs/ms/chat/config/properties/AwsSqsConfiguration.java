package com.symphony.sfs.ms.chat.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@ConfigurationProperties("aws")
@Component
public class AwsSqsConfiguration {
  private Sqs sqs = new Sqs();

  @Data
  public static class Sqs {
    private String endpoint;
    private String signinRegion;
  }
}
