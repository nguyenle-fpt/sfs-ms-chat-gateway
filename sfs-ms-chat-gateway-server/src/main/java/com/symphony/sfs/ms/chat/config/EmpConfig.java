package com.symphony.sfs.ms.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@ConfigurationProperties("emp")
@Component
public class EmpConfig {
  private Map<String, Integer> maxTextLength;
}
