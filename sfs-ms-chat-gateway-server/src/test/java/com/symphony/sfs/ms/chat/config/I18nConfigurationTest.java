package com.symphony.sfs.ms.chat.config;

import com.symphony.sfs.ms.starter.i18n.DatabaseMessageSource;
import com.symphony.sfs.ms.starter.i18n.MessageEntityRepository;
import org.junit.jupiter.api.Test;

import javax.mail.Message;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class I18nConfigurationTest {

  @Test
  void additionalMessageSources() {

    MessageEntityRepository messageEntityRepository = mock(MessageEntityRepository.class);
    DatabaseMessageSource databaseMessageSource = new DatabaseMessageSource(messageEntityRepository);

    assertEquals(Collections.singletonList(databaseMessageSource), new I18nConfiguration(databaseMessageSource).additionalMessageSources());
  }
}
