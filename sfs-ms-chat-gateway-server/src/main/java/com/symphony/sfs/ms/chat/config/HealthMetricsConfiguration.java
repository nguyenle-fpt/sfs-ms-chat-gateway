package com.symphony.sfs.ms.chat.config;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.symphony.sfs.ms.chat.health.SqsHealthIndicator;
import com.symphony.sfs.ms.chat.service.EmpMicroserviceResolver;
import com.symphony.sfs.ms.starter.health.CachedHealthIndicator;
import com.symphony.sfs.ms.starter.health.DynamoDbHealthIndicator;
import com.symphony.sfs.ms.starter.health.HealthMeterService;
import com.symphony.sfs.ms.starter.health.MicroserviceHealthIndicator;
import com.symphony.sfs.ms.starter.health.TimeoutHealthIndicator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.HealthIndicator;
import com.symphony.sfs.ms.chat.health.QueueListenerHealthIndicator;
import org.springframework.cloud.aws.messaging.listener.SimpleMessageListenerContainer;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;

import static com.symphony.sfs.ms.starter.util.HealthUtils.buildHealthIndicatorForMicroservices;

@Configuration
@RequiredArgsConstructor
public class HealthMetricsConfiguration {
  private final HealthMeterService healthMeterService;
  private final EmpMicroserviceResolver empMicroserviceResolver;
  private final WebClient webClient;
  private final AmazonDynamoDB amazonDynamoDB;
  private final AmazonSQSAsync amazonSqs;
  private final SimpleMessageListenerContainer queueListener;

  @PostConstruct
  public void initHealthMeters() {
    Duration cacheTtl = healthMeterService.getCacheTtl();

    HealthIndicator dynamoHealth = new CachedHealthIndicator(cacheTtl, new DynamoDbHealthIndicator(amazonDynamoDB));
    healthMeterService.registerHealthMeter("db", dynamoHealth);

    HealthIndicator sqsHealth = new CachedHealthIndicator(cacheTtl, new SqsHealthIndicator(amazonSqs));
    healthMeterService.registerHealthMeter("sqs", sqsHealth);

    HealthIndicator queueListenersHealth = new CachedHealthIndicator(cacheTtl, new QueueListenerHealthIndicator(amazonSqs, queueListener));
    healthMeterService.registerHealthMeter("sqs-listeners", queueListenersHealth);

    List<MicroserviceHealthIndicator> indicators = buildHealthIndicatorForMicroservices(webClient, empMicroserviceResolver.getAllEmpMicroserviceBaseUris());
    indicators.forEach(indicator -> healthMeterService.registerHealthMeter(indicator.getMicroserviceName(), getCachedTimeoutIndicator(indicator)));
  }

  private HealthIndicator getCachedTimeoutIndicator(HealthIndicator indicator) {
    Duration cacheTtl = healthMeterService.getCacheTtl();
    Duration timeout = healthMeterService.getTimeout();
    return new CachedHealthIndicator(cacheTtl, new TimeoutHealthIndicator(timeout, indicator));
  }

}
