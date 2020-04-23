package com.symphony.sfs.ms.chat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.symphony.sfs.ms.starter.security.AuthorizationService;
import com.symphony.sfs.ms.starter.security.JwtAuthorizationFilterFactory;
import com.symphony.sfs.ms.starter.security.SymphonyJwtAuthorizationFilter;
import com.symphony.sfs.ms.starter.util.AuthorizeRequestsConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebSecurityConfiguration {

  private final ObjectMapper objectMapper;
  private final AuthorizationService authenticationService;

  public WebSecurityConfiguration(ObjectMapper objectMapper, AuthorizationService authenticationService) {
    this.objectMapper = objectMapper;
    this.authenticationService = authenticationService;
  }

  @Bean
  public JwtAuthorizationFilterFactory jwtAuthorizationFilterFactory() {
    return SymphonyJwtAuthorizationFilter.factory(authenticationService, objectMapper);
  }

  @Bean
  public AuthorizeRequestsConfigurer authorizeRequestsConfigurer() {
    return registry -> registry
      .antMatchers("/api/v1/internal/messages/**").permitAll();
  }
}
