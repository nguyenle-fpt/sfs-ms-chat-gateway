package com.symphony.sfs.ms.chat.config;

import com.symphony.sfs.ms.starter.i18n.MessageSourceChain;
import com.symphony.sfs.ms.starter.symphony.crypto.ContentKeyManager;
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


  @Override
  public void customize(ConcurrentMapCacheManager cacheManager) {
    cacheManager.setCacheNames(Arrays.asList(ContentKeyManager.CONTENT_KEY_CACHE, MessageSourceChain.CACHE_NAME));
  }

  @CacheEvict(allEntries = true, value = {ContentKeyManager.CONTENT_KEY_CACHE})
  @Scheduled(fixedDelayString = "${microservice.chat.key-cache.ttl:7200000}")
  public void clearContentKeyCache() {
    LOG.info("Cleared content key cache");
  }
}
