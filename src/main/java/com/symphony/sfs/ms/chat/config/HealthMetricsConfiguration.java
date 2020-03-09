package com.symphony.sfs.ms.chat.config;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.symphony.sfs.ms.chat.service.EmpMicroserviceResolver;
import com.symphony.sfs.ms.starter.health.CachedHealthIndicator;
import com.symphony.sfs.ms.starter.health.DynamoDbHealthIndicator;
import com.symphony.sfs.ms.starter.health.HealthMeterService;
import com.symphony.sfs.ms.starter.health.MicroserviceHealthIndicator;
import com.symphony.sfs.ms.starter.health.TimeoutHealthIndicator;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;

import static com.symphony.sfs.ms.starter.util.HealthUtils.buildHealthIndicatorForMicroservices;

@Configuration
public class HealthMetricsConfiguration {
  private HealthMeterService healthMeterService;
  private EmpMicroserviceResolver empMicroserviceResolver;
  private WebClient webClient;
  private AmazonDynamoDB amazonDynamoDB;

  public HealthMetricsConfiguration(HealthMeterService healthMeterService, EmpMicroserviceResolver empMicroserviceResolver, WebClient webClient, AmazonDynamoDB amazonDynamoDB) {
    this.healthMeterService = healthMeterService;
    this.empMicroserviceResolver = empMicroserviceResolver;
    this.webClient = webClient;
    this.amazonDynamoDB = amazonDynamoDB;
  }

  @PostConstruct
  public void initHealthMeters() {
    Duration cacheTtl = healthMeterService.getCacheTtl();

    HealthIndicator dynamoHealth = new CachedHealthIndicator(cacheTtl, new DynamoDbHealthIndicator(amazonDynamoDB));
    healthMeterService.registerHealthMeter("db", dynamoHealth);

    List<MicroserviceHealthIndicator> indicators = buildHealthIndicatorForMicroservices(webClient, empMicroserviceResolver.getAllEmpMicroserviceBaseUris());
    indicators.forEach(indicator -> healthMeterService.registerHealthMeter(indicator.getMicroserviceName(), getCachedTimeoutIndicator(indicator)));
  }

  private HealthIndicator getCachedTimeoutIndicator(HealthIndicator indicator) {
    Duration cacheTtl = healthMeterService.getCacheTtl();
    Duration timeout = healthMeterService.getTimeout();
    return new CachedHealthIndicator(cacheTtl, new TimeoutHealthIndicator(timeout, indicator));
  }
}
