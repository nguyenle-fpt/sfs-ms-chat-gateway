package com.symphony.sfs.ms.chat.config;

import com.symphony.sfs.ms.starter.i18n.MessageSourceChain;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CachingConfigurationTest {

  @Test
  void customize() {
    ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
    new CachingConfiguration().customize(cacheManager);

    assertEquals(new HashSet<>(Arrays.asList(CachingConfiguration.CONTENT_KEY_CACHE, MessageSourceChain.CACHE_NAME)), new HashSet<>(cacheManager.getCacheNames()));
  }

  @Test
  void clearContentKeyCache() {
    new CachingConfiguration().clearContentKeyCache();
  }
}
