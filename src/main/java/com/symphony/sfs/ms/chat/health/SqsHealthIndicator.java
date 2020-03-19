package com.symphony.sfs.ms.chat.health;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Custom health indicator for DynamoDB.
 */
@Slf4j
public class SqsHealthIndicator implements HealthIndicator {

  private final AmazonSQSAsync amazonSqs;

  public SqsHealthIndicator(AmazonSQSAsync amazonSqs) {
    this.amazonSqs = amazonSqs;
  }

  @Override
  public Health health() {
    try {
      if (!amazonSqs.listQueues().getQueueUrls().isEmpty()) {
        return Health.up().build();
      }
    } catch (Exception e) {
      LOG.error("There was an error while checking SQS connection status", e);
    }
    return Health.down().build();
  }
}
