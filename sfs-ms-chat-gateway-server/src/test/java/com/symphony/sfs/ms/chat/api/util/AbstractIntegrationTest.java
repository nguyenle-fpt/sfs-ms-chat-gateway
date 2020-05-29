package com.symphony.sfs.ms.chat.api.util;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.symphony.sfs.ms.chat.config.DynamoConfiguration;
import com.symphony.sfs.ms.chat.config.HandlebarsConfiguration;
import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.datafeed.ContentKeyManager;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.datafeed.MessageDecryptor;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.ChannelService;
import com.symphony.sfs.ms.chat.service.ConnectionRequestManager;
import com.symphony.sfs.ms.chat.service.FederatedAccountSessionService;
import com.symphony.sfs.ms.chat.service.SymphonyMessageService;
import com.symphony.sfs.ms.chat.service.SymphonyService;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.chat.service.external.MockAdminClient;
import com.symphony.sfs.ms.chat.service.external.MockEmpClient;
import com.symphony.sfs.ms.chat.util.SymphonySystemMessageTemplateProcessor;
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
import com.symphony.sfs.ms.starter.util.RsaUtils;
import io.fabric8.mockwebserver.DefaultMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.reactive.function.client.WebClient;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class AbstractIntegrationTest implements ConfiguredDynamoTest, LocalProfileTest, RestApiTest {
  protected AuthenticationService authenticationService;
  protected ObjectMapper objectMapper;
  protected DynamoConfiguration dynamoConfiguration;
  protected PodConfiguration podConfiguration;
  protected ChatConfiguration chatConfiguration;
  protected HandlebarsConfiguration handlebarsConfiguration;
  protected BotConfiguration botConfiguration;
  protected DatafeedSessionPool datafeedSessionPool;
  protected EmpClient empClient;
  protected DefaultMockServer mockServer;
  protected WebClient webClient;
  protected StreamService streamService;
  protected SymphonyMessageService symphonyMessageService;
  protected SymphonySystemMessageTemplateProcessor symphonySystemMessageTemplateProcessor;
  protected ConnectionsService connectionsServices;
  protected ConnectionRequestManager connectionRequestManager;
  protected ChannelService channelService;
  protected ForwarderQueueConsumer forwarderQueueConsumer;
  protected FederatedAccountSessionService federatedAccountSessionService;
  protected FederatedAccountRepository federatedAccountRepository;
  protected KeyPair keyPair;
  protected AdminClient adminClient;

  @BeforeEach
  public void setUp(AmazonDynamoDB db, DefaultMockServer mockServer) throws Exception {
    this.mockServer = mockServer;
    webClient = buildTestClient();
    SymphonyService symphonyService = mock(SymphonyService.class);

    dynamoConfiguration = provisionTestTable(db);
    objectMapper = new JacksonConfiguration().configureJackson(new ObjectMapper());

    // keys
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    keyPair = kpg.generateKeyPair();

    // configurations
    podConfiguration = new PodConfiguration();
    podConfiguration.setUrl("https://" + mockServer.getHostName() + ":" + mockServer.getPort());
    podConfiguration.setKeyAuth(podConfiguration.getUrl());
    podConfiguration.setSessionAuth(podConfiguration.getUrl());

    botConfiguration = new BotConfiguration();
    botConfiguration.setPrivateKey(new Key(RsaUtils.encodeRSAKey(keyPair.getPrivate())));
    botConfiguration.setUsername("bot");

    chatConfiguration = new ChatConfiguration();
    chatConfiguration.setSharedPrivateKey(new Key(RsaUtils.encodeRSAKey(keyPair.getPrivate())));
    chatConfiguration.setSharedPublicKey(new Key(RsaUtils.encodeRSAKey(keyPair.getPublic())));

    handlebarsConfiguration = new HandlebarsConfiguration();

    // emp client
    empClient = new MockEmpClient();
    adminClient = new MockAdminClient();

    // authentication
    authenticationService = mock(AuthenticationService.class);

    // account and datafeed
    federatedAccountRepository = new FederatedAccountRepository(db, dynamoConfiguration.getDynamoSchema());
    federatedAccountSessionService = new FederatedAccountSessionService(federatedAccountRepository);
    datafeedSessionPool = new DatafeedSessionPool(authenticationService, podConfiguration, chatConfiguration, federatedAccountSessionService);

    ContentKeyManager contentKeyManager = new ContentKeyManager(podConfiguration, datafeedSessionPool);
    MessageDecryptor messageDecryptor = new MessageDecryptor(contentKeyManager);
    forwarderQueueConsumer = new ForwarderQueueConsumer(objectMapper, messageDecryptor, datafeedSessionPool);

    // services
    streamService = spy(new StreamService(webClient));
    symphonySystemMessageTemplateProcessor = spy(new SymphonySystemMessageTemplateProcessor(handlebarsConfiguration.handlebars()));
    symphonyMessageService = spy(new SymphonyMessageService(podConfiguration, chatConfiguration, authenticationService, federatedAccountRepository, streamService, symphonySystemMessageTemplateProcessor, symphonyService, datafeedSessionPool));
    connectionsServices = new ConnectionsService(webClient);
    connectionRequestManager = spy(new ConnectionRequestManager(connectionsServices, podConfiguration));
    channelService = new ChannelService(streamService, symphonyMessageService, podConfiguration, empClient, forwarderQueueConsumer, datafeedSessionPool, federatedAccountRepository, adminClient, symphonyService);
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
