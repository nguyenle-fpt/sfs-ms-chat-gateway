package com.symphony.sfs.ms.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Arrays;

import static com.symphony.sfs.ms.chat.service.JwtTokenGenerator.MICROSERVICE_NAME;

@Configuration
@EnableCaching
@Slf4j
public class CachingConfiguration implements CacheManagerCustomizer<ConcurrentMapCacheManager> {

  public static final String CONTENT_KEY_CACHE = MICROSERVICE_NAME + ":content-key-cache";

  @Override
  public void customize(ConcurrentMapCacheManager cacheManager) {
    cacheManager.setCacheNames(Arrays.asList(CONTENT_KEY_CACHE));
  }

  @CacheEvict(allEntries = true, value = {CONTENT_KEY_CACHE})
  @Scheduled(fixedDelayString = "${microservice.chat.key-cache.ttl:7200000}")
  public void clearContentKeyCache() {
    LOG.info("Cleared content key cache");
  }
}
