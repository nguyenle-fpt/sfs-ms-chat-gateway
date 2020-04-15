package com.symphony.sfs.ms.chat.service;

import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.oss.models.core.canon.facade.PodAndUserId;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.exception.FederatedAccountNotFoundProblem;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.config.properties.common.Key;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.UserSession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;

import static com.symphony.sfs.ms.starter.testing.MockitoUtils.once;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageServiceTest {

  private MessageService messageService;

  private EmpClient empClient;
  private AuthenticationService authenticationService;
  private PodConfiguration podConfiguration;
  private BotConfiguration botConfiguration;
  private FederatedAccountRepository federatedAccountRepository;
  private StreamService streamService;


  private UserSession userSession;

  @BeforeEach
  public void setUp() {

    empClient = mock(EmpClient.class);
    federatedAccountRepository = mock(FederatedAccountRepository.class);
    streamService = mock(StreamService.class);
    authenticationService = mock(AuthenticationService.class);

    botConfiguration = new BotConfiguration();
    botConfiguration.setUsername("username");
    botConfiguration.setEmailAddress("emailAddress");
    botConfiguration.setPrivateKey(new Key("-----botConfigurationPrivateKey"));

    podConfiguration = new PodConfiguration();
    podConfiguration.setUrl("podUrl");
    podConfiguration.setSessionAuth("sessionAuth");
    podConfiguration.setKeyAuth("keyAuth");


    userSession = new UserSession("username", "jwt", "kmToken", "sessionToken");
    when(authenticationService.authenticate(anyString(), anyString(), anyString(), anyString())).thenReturn(userSession);
    messageService = new MessageService(empClient, federatedAccountRepository, mock(ForwarderQueueConsumer.class));
  }

  @Test
  void onIMMessage() {

    FederatedAccount toFederatedAccount = FederatedAccount.builder()
      .emp("emp")
      .symphonyUserId("234567891")
      .build();

    IUser fromSymphonyUser = newSymphonyUser("123456789");

    long now = OffsetDateTime.now().toEpochSecond();


    when(federatedAccountRepository.findBySymphonyId("234567891")).thenReturn(Optional.of(toFederatedAccount));
    when(empClient.sendMessage("emp", "streamId", "messageId", fromSymphonyUser, toFederatedAccount, now, "text")).thenReturn(Optional.of("leaseId"));
    messageService.onIMMessage("streamId", "messageId", fromSymphonyUser, Arrays.asList("123456789", "234567891"), now, "text");

    verify(federatedAccountRepository, once()).findBySymphonyId("123456789");
    verify(federatedAccountRepository, once()).findBySymphonyId("234567891");
    verify(empClient, once()).sendMessage("emp", "streamId", "messageId", fromSymphonyUser, toFederatedAccount, now, "text");
  }

  @Test
  void onIMMessage_FederatedServiceAccountNotFound() {

    IUser fromSymphonyUser = newSymphonyUser("123456789");
    long now = OffsetDateTime.now().toEpochSecond();

    when(federatedAccountRepository.findBySymphonyId("123456789")).thenReturn(Optional.empty());
    assertThrows(FederatedAccountNotFoundProblem.class, () -> messageService.onIMMessage("streamId", "messageId", fromSymphonyUser, Arrays.asList("123456789", "234567891"), now, "text"));
  }

  private IUser newSymphonyUser(String symphonyUserId) {

    PodAndUserId id = PodAndUserId.newBuilder().build(Long.valueOf(symphonyUserId));
    IUser mockIUser = mock(IUser.class);
    when(mockIUser.getId()).thenReturn(id);

    return mockIUser;

  }
}
