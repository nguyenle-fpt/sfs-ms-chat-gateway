package com.symphony.sfs.ms.chat.config;

import com.symphony.sfs.ms.starter.health.PodVersionChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@RequiredArgsConstructor
@Configuration
public class ChatGatewayConfiguration {

  private final WebClient webClient;

  @Bean
  public PodVersionChecker podVersionChecker(){
    return new PodVersionChecker(webClient);
  }

}
