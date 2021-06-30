package org.springframework.cloud.aws.messaging.listener;

import com.symphony.sfs.ms.chat.config.SfsSimpleMessageListenerContainer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class QueueListenerUtils {
  public static Set<String> getRegisteredQueueNames(SfsSimpleMessageListenerContainer listener) {
    return listener.getRegisteredQueues().keySet();
  }
}
