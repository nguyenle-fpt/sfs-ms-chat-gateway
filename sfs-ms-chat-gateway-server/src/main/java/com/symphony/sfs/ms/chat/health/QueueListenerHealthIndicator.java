package com.symphony.sfs.ms.chat.health;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.QueueDoesNotExistException;
import com.symphony.sfs.ms.chat.config.SfsSimpleMessageListenerContainer;
import io.awspring.cloud.messaging.listener.SimpleMessageListenerContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.Set;

import static org.springframework.cloud.aws.messaging.listener.QueueListenerUtils.getRegisteredQueueNames;

@Slf4j
public class QueueListenerHealthIndicator implements HealthIndicator {

  private final AmazonSQSAsync amazonSqs;
  private final SfsSimpleMessageListenerContainer listener;

  public QueueListenerHealthIndicator(AmazonSQSAsync amazonSqs, SfsSimpleMessageListenerContainer listener) {
    this.amazonSqs = amazonSqs;
    this.listener = listener;
  }

  @Override
  public Health health() {
    Set<String> queueNames = getRegisteredQueueNames(listener);
    boolean allListenersRunning = true;

    Health.Builder builder = new Health.Builder();
    LOG.debug("Registered queues: {}", queueNames);
    for (String queueName : queueNames) {
      if (!listener.isRunning(queueName)) {
        LOG.warn("Listener for queue '{}' is not running", queueName);
        builder.down().withDetail(queueName, "listener is not running");
        allListenersRunning = false;
      }

      if (!isQueueReachable(queueName)) {
        builder.down().withDetail(queueName, "queue is not reachable");
        allListenersRunning = false;
      }
    }
    if (allListenersRunning) {
      builder.up();
    }
    return builder.build();
  }

  private boolean isQueueReachable(String queueName) {
    try {
      amazonSqs.getQueueUrl(queueName);
      return true;
    } catch (QueueDoesNotExistException e) {
      LOG.warn("Queue '{}' does not exist", queueName);
      return false;
    } catch (SdkClientException e) {
      LOG.error("Queue '{}' is not reachable", queueName, e);
      return false;
    }
  }
}
