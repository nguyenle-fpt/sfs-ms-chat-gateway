package com.symphony.sfs.ms.chat.config;

import com.symphony.sfs.ms.chat.service.JwtTokenGenerator;
import com.symphony.sfs.ms.starter.health.MeterConfigurator;
import io.micrometer.core.instrument.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class MetricsConfiguration {

  @Bean
  public MeterConfigurator meterConfigurator() {
    return () -> List.of(Tag.of("microservice", JwtTokenGenerator.MICROSERVICE_NAME));
  }
}
