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
import com.symphony.sfs.ms.chat.service.SymphonyMessageSender;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyService;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.chat.service.external.MockAdminClient;
import com.symphony.sfs.ms.chat.service.external.MockEmpClient;
import com.symphony.sfs.ms.chat.util.SymphonySystemMessageTemplateProcessor;
import com.symphony.sfs.ms.starter.config.JacksonConfiguration;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.config.properties.common.PemResource;
import com.symphony.sfs.ms.starter.health.MeterManager;
import com.symphony.sfs.ms.starter.security.SessionManager;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonyAuthFactory;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.symphony.xpod.ConnectionsService;
import com.symphony.sfs.ms.starter.testing.LocalProfileTest;
import com.symphony.sfs.ms.starter.testing.RestApiTest;
import com.symphony.sfs.ms.starter.util.RsaUtils;
import io.fabric8.mockwebserver.DefaultMockServer;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.reactive.function.client.WebClient;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Collections;
import java.util.Optional;
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
  protected SymphonyMessageSender symphonyMessageSender;
  protected SymphonySystemMessageTemplateProcessor symphonySystemMessageTemplateProcessor;
  protected ConnectionsService connectionsServices;
  protected ConnectionRequestManager connectionRequestManager;
  protected ChannelService channelService;
  protected ForwarderQueueConsumer forwarderQueueConsumer;
  protected FederatedAccountSessionService federatedAccountSessionService;
  protected FederatedAccountRepository federatedAccountRepository;
  protected KeyPair keyPair;
  protected AdminClient adminClient;
  protected MeterManager meterManager;
  protected SymphonyAuthFactory symphonyAuthFactory;

  @BeforeEach
  public void setUp(AmazonDynamoDB db, DefaultMockServer mockServer) throws Exception {
    this.mockServer = mockServer;
    webClient = buildTestClient();
    SymphonyService symphonyService = mock(SymphonyService.class);

    dynamoConfiguration = provisionTestTable(db);
    objectMapper = new JacksonConfiguration().configureJackson(new ObjectMapper());

    meterManager = new MeterManager(new SimpleMeterRegistry(), Optional.empty());

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
    botConfiguration.setPrivateKey(new PemResource(RsaUtils.encodeRSAKey(keyPair.getPrivate())));
    botConfiguration.setUsername("bot");

    chatConfiguration = new ChatConfiguration();
    chatConfiguration.setSharedPrivateKey(new PemResource(RsaUtils.encodeRSAKey(keyPair.getPrivate())));
    chatConfiguration.setSharedPublicKey(new PemResource(RsaUtils.encodeRSAKey(keyPair.getPublic())));

    handlebarsConfiguration = new HandlebarsConfiguration();

    // emp client
    empClient = new MockEmpClient();
    adminClient = new MockAdminClient();

    // authentication
    authenticationService = mock(AuthenticationService.class);
    symphonyAuthFactory = new SymphonyAuthFactory(authenticationService, null, podConfiguration, botConfiguration, null);

    // account and datafeed
    federatedAccountRepository = new FederatedAccountRepository(db, dynamoConfiguration.getDynamoSchema());
    federatedAccountSessionService = new FederatedAccountSessionService(federatedAccountRepository);
    datafeedSessionPool = new DatafeedSessionPool(authenticationService, podConfiguration, chatConfiguration, federatedAccountSessionService, meterManager);

    ContentKeyManager contentKeyManager = new ContentKeyManager(podConfiguration, datafeedSessionPool);
    MessageDecryptor messageDecryptor = new MessageDecryptor(contentKeyManager);
    forwarderQueueConsumer = new ForwarderQueueConsumer(objectMapper, messageDecryptor, datafeedSessionPool, meterManager);

    SymphonyAuthFactory symphonyAuthFactory = new SymphonyAuthFactory(authenticationService, null, podConfiguration, botConfiguration, null);
    SessionManager sessionManager = new SessionManager(webClient, Collections.emptyList());

    // services
    streamService = spy(new StreamService(sessionManager));
    symphonySystemMessageTemplateProcessor = spy(new SymphonySystemMessageTemplateProcessor(handlebarsConfiguration.handlebars()));
    symphonyMessageSender = spy(new SymphonyMessageSender(podConfiguration, chatConfiguration, authenticationService, federatedAccountRepository, streamService, symphonySystemMessageTemplateProcessor));
    connectionsServices = new ConnectionsService(sessionManager);
    connectionRequestManager = spy(new ConnectionRequestManager(connectionsServices, podConfiguration));
    channelService = new ChannelService(streamService, symphonyMessageSender, podConfiguration, empClient, forwarderQueueConsumer, datafeedSessionPool, federatedAccountRepository, adminClient, symphonyService);
    channelService.registerAsDatafeedListener();
  }

  @AfterEach
  public void tearDown(AmazonDynamoDB db) {
    deleteTestTable(db);
  }

  public SymphonySession getSession(String username) {
    return new SymphonySession(username, UUID.randomUUID().toString(), UUID.randomUUID().toString());
  }
}
