package com.symphony.sfs.ms.chat.service;

import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.starter.exception.ConfigurationException;
import com.symphony.sfs.ms.starter.security.SfsJwtToken;
import com.symphony.sfs.ms.starter.util.RsaUtils;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;

@Component
@Getter
public class JwtTokenGenerator {
  public static final String MICROSERVICE_NAME = "sfs-ms-chat-gateway";

  private final ChatConfiguration chatConfiguration;

  private PrivateKey privateKey;

  public JwtTokenGenerator(ChatConfiguration chatConfiguration) {
    this.chatConfiguration = chatConfiguration;
    try {
      this.privateKey = RsaUtils.parseRSAPrivateKey(chatConfiguration.getPrivateKey().getData());
    } catch (GeneralSecurityException e) {
      throw new ConfigurationException("Unable to parse microservice private key");
    }
  }

  public String generateMicroserviceToken() {
    return SfsJwtToken.generateForMicroservice(MICROSERVICE_NAME, privateKey);
  }

}
