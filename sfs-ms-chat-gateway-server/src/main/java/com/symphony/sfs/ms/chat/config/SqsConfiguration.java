package com.symphony.sfs.ms.chat.config;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import com.symphony.sfs.ms.chat.config.properties.AwsSqsConfiguration;
import org.springframework.cloud.aws.messaging.config.QueueMessageHandlerFactory;
import org.springframework.cloud.aws.messaging.listener.QueueMessageHandler;
import org.springframework.cloud.aws.messaging.listener.SimpleMessageListenerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class SqsConfiguration {
  private AwsSqsConfiguration configuration;

  public SqsConfiguration(AwsSqsConfiguration configuration) {
    this.configuration = configuration;
  }

  @Bean
  public AmazonSQSAsync amazonSqsClient(AWSCredentialsProvider awsCredentialsProvider) {
    return AmazonSQSAsyncClientBuilder.standard()
      .withCredentials(awsCredentialsProvider)
      .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(configuration.getSqs().getEndpoint(), configuration.getSqs().getSigninRegion()))
      .build();
  }

  @Bean
  public SimpleMessageListenerContainer simpleMessageListenerContainer(AmazonSQSAsync amazonSQSAsync, QueueMessageHandler queueMessageHandler) {
    SimpleMessageListenerContainer simpleMessageListenerContainer = new SimpleMessageListenerContainer();
    simpleMessageListenerContainer.setAmazonSqs(amazonSQSAsync);
    simpleMessageListenerContainer.setMessageHandler(queueMessageHandler);
    simpleMessageListenerContainer.setMaxNumberOfMessages(10);
    simpleMessageListenerContainer.setTaskExecutor(threadPoolTaskExecutor());
    return simpleMessageListenerContainer;
  }

  @Bean
  public QueueMessageHandler queueMessageHandler(AmazonSQSAsync amazonSQSAsync) {
    QueueMessageHandlerFactory queueMessageHandlerFactory = new QueueMessageHandlerFactory();
    queueMessageHandlerFactory.setAmazonSqs(amazonSQSAsync);
    QueueMessageHandler queueMessageHandler = queueMessageHandlerFactory.createQueueMessageHandler();
    return queueMessageHandler;
  }

  private ThreadPoolTaskExecutor threadPoolTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(100);
    executor.setKeepAliveSeconds(60);
    executor.initialize();
    return executor;
  }
}
