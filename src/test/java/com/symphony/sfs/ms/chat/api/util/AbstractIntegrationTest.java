package com.symphony.sfs.ms.chat.api.util;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.symphony.sfs.ms.chat.config.DynamoConfiguration;
import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.datafeed.ContentKeyManager;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.datafeed.MessageDecryptor;
import com.symphony.sfs.ms.chat.service.ChannelService;
import com.symphony.sfs.ms.chat.service.ConnectionRequestManager;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.chat.service.external.MockEmpClient;
import com.symphony.sfs.ms.starter.config.JacksonConfiguration;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.config.properties.common.Key;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.UserSession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.symphony.xpod.ConnectionsService;
import com.symphony.sfs.ms.starter.testing.LocalProfileTest;
import com.symphony.sfs.ms.starter.testing.RestApiTest;
import io.fabric8.mockwebserver.DefaultMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.reactive.function.client.WebClient;

import javax.net.ssl.SSLException;
import java.util.UUID;

import static org.mockito.Mockito.mock;

public class AbstractIntegrationTest implements ConfiguredDynamoTest, LocalProfileTest, RestApiTest {
  protected AuthenticationService authenticationService;
  protected ObjectMapper objectMapper;
  protected DynamoConfiguration dynamoConfiguration;
  protected PodConfiguration podConfiguration;
  protected ChatConfiguration chatConfiguration;
  protected BotConfiguration botConfiguration;
  protected DatafeedSessionPool datafeedSessionPool;
  protected EmpClient empClient;
  protected DefaultMockServer mockServer;
  protected WebClient webClient;
  protected StreamService streamService;
  protected ConnectionsService connectionsServices;
  protected ConnectionRequestManager connectionRequestManager;
  protected ChannelService channelService;
  protected ForwarderQueueConsumer forwarderQueueConsumer;

  @BeforeEach
  public void setUp(AmazonDynamoDB db, DefaultMockServer mockServer) throws SSLException {
    this.mockServer = mockServer;
    webClient = buildTestClient();

    dynamoConfiguration = provisionTestTable(db);
    objectMapper = new JacksonConfiguration().configureJackson(new ObjectMapper());

    // configurations
    podConfiguration = new PodConfiguration();
    podConfiguration.setUrl(mockServer.getHostName() + ":" + mockServer.getPort());
    podConfiguration.setKeyAuth(podConfiguration.getUrl());
    podConfiguration.setSessionAuth(podConfiguration.getUrl());

    botConfiguration = new BotConfiguration();
    botConfiguration.setPrivateKey(mock(Key.class));
    botConfiguration.setUsername("bot");

    chatConfiguration = new ChatConfiguration();
    chatConfiguration.setSharedPrivateKey(mock(Key.class));
    chatConfiguration.setSharedPublicKey(mock(Key.class));

    // emp client
    empClient = new MockEmpClient();

    // authentication
    authenticationService = mock(AuthenticationService.class);

    // datafeed
    datafeedSessionPool = new DatafeedSessionPool(authenticationService, podConfiguration, chatConfiguration);

    ContentKeyManager contentKeyManager = new ContentKeyManager(podConfiguration, datafeedSessionPool);
    MessageDecryptor messageDecryptor = new MessageDecryptor(contentKeyManager);
    forwarderQueueConsumer = new ForwarderQueueConsumer(objectMapper, messageDecryptor);

    // services
    streamService = new StreamService(webClient);
    connectionsServices = new ConnectionsService(webClient);
    connectionRequestManager = new ConnectionRequestManager(connectionsServices, podConfiguration);
    channelService = new ChannelService(streamService, authenticationService, podConfiguration, chatConfiguration, empClient, forwarderQueueConsumer);
    channelService.registerAsDatafeedListener();
  }

  @AfterEach
  public void tearDown(AmazonDynamoDB db) {
    deleteTestTable(db);
  }

  public UserSession getSession(String username) {
    return new UserSession(username, "jwt", UUID.randomUUID().toString(), UUID.randomUUID().toString());
  }
}
