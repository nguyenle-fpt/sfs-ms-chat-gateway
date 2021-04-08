package com.symphony.sfs.ms.chat.config;

import com.symphony.sfs.ms.starter.i18n.DatabaseMessageSource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * Additional configuration taken into account in {@link com.symphony.sfs.ms.starter.i18n.DefaultI18nConfiguration}
 */
@Configuration
@RequiredArgsConstructor
public class I18nConfiguration {

  private final DatabaseMessageSource databaseMessageSource;

  @Bean
  public List<MessageSource> additionalMessageSources() {
    return Arrays.asList(databaseMessageSource);
  }
}
