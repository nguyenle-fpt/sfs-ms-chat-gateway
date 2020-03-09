package com.symphony.sfs.ms.chat.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

@Data
@Component
@ConfigurationProperties("microservice.chat")
@Validated
public class ChatConfiguration {
  @NotBlank
  private String msAdminUrl;
}
