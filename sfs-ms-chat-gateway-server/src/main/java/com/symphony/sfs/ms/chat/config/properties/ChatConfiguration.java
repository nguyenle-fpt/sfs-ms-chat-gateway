package com.symphony.sfs.ms.chat.config.properties;

import com.symphony.sfs.ms.starter.config.properties.common.PemResource;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Component
@ConfigurationProperties("microservice.chat")
@Validated
public class ChatConfiguration {
  @NotBlank
  private String msAdminUrl;

  @NotNull
  private PemResource sharedPublicKey;

  @NotNull
  private PemResource sharedPrivateKey;

  @NotNull
  private PemResource privateKey;

  private String stopImCreationAt;
}
