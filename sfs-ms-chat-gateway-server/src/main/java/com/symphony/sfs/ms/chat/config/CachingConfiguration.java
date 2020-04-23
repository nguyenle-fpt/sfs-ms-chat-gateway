package com.symphony.sfs.ms.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
@EnableCaching
@Slf4j
public class CachingConfiguration implements CacheManagerCustomizer<ConcurrentMapCacheManager> {

  public static final String CONTENT_KEY_CACHE = "sfs-ms-chat-gateway:content-key-cache";

  @Override
  public void customize(ConcurrentMapCacheManager cacheManager) {
    cacheManager.setCacheNames(Arrays.asList(CONTENT_KEY_CACHE));
  }
}
