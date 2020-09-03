package org.springframework.cloud.aws.messaging.listener;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class QueueListenerUtils {
  public static Set<String> getRegisteredQueueNames(SimpleMessageListenerContainer listener) {
    return listener.getRegisteredQueues().keySet();
  }
}
