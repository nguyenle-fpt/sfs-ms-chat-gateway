package com.symphony.sfs.ms.chat.config;

import io.awspring.cloud.messaging.listener.SimpleMessageListenerContainer;

import java.util.Map;

/**
 * Upgrade to io.awspring.cloud:spring-cloud-aws-messaging:2.3.0: https://spring.io/blog/2021/03/17/spring-cloud-aws-2-3-is-now-available
 *
 * SimpleMessageListenerContainer.getRegisteredQueues is not public anymore
 *
 * Override getRegisteredQueues to make it public because we currently use it
 */
public class SfsSimpleMessageListenerContainer extends SimpleMessageListenerContainer {
  @Override
  public Map<String, QueueAttributes> getRegisteredQueues() {
    return super.getRegisteredQueues();
  }
}
