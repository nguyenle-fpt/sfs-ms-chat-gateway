package com.symphony.sfs.ms.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
@Slf4j
public class CachingConfiguration implements CacheManagerCustomizer<ConcurrentMapCacheManager> {

  @Override
  public void customize(ConcurrentMapCacheManager cacheManager) {
  }
}
