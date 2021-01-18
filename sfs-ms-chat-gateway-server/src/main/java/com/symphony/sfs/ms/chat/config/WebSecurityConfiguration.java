package com.symphony.sfs.ms.chat.config;

import com.symphony.sfs.ms.starter.security.AuthorizationService;
import com.symphony.sfs.ms.starter.security.JwtAuthorizationFilterFactory;
import com.symphony.sfs.ms.starter.security.SecurityConstants;
import com.symphony.sfs.ms.starter.security.SymphonyJwtAuthorizationFilter;
import com.symphony.sfs.ms.starter.util.AuthorizeRequestsConfigurer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;

@Configuration
@RequiredArgsConstructor
public class WebSecurityConfiguration {

  private final AuthorizationService authorizationService;

  @Bean
  public JwtAuthorizationFilterFactory jwtAuthorizationFilterFactory() {
    return SymphonyJwtAuthorizationFilter.factory(authorizationService);
  }

  @Bean
  public AuthorizeRequestsConfigurer authorizeRequestsConfigurer() {
    return registry -> registry
      .antMatchers("/api/v1/internal/**").hasAnyRole(SecurityConstants.STANDARD, SecurityConstants.MICROSERVICE);
  }

  @Bean
  public HttpFirewall allowCharactersInUrl() {
    StrictHttpFirewall firewall = new StrictHttpFirewall();
    firewall.setAllowUrlEncodedDoubleSlash(true);
    firewall.setAllowUrlEncodedSlash(true);
    firewall.setAllowUrlEncodedPercent(true);
    return firewall;
  }
}
