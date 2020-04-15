package com.symphony.sfs.ms.chat.service;

import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.generated.model.SendMessageFailedProblem;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.config.properties.common.Key;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.UserSession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.symphony.sfs.ms.starter.testing.MockitoUtils.once;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SymphonyMessageServiceTest {

  private UserSession userSession;
  private AuthenticationService authenticationService;
  private PodConfiguration podConfiguration;
  //  private BotConfiguration botConfiguration;
  private ChatConfiguration chatConfiguration;

  private FederatedAccountRepository federatedAccountRepository;
  private StreamService streamService;

  private SymphonyMessageService symphonyMessageService;

  @BeforeEach
  public void setUp() {

//    botConfiguration = new BotConfiguration();
//    botConfiguration.setUsername("username");
//    botConfiguration.setEmailAddress("emailAddress");
//    botConfiguration.setPrivateKey(new Key("-----botConfigurationPrivateKey"));

    podConfiguration = new PodConfiguration();
    podConfiguration.setUrl("podUrl");
    podConfiguration.setSessionAuth("sessionAuth");
    podConfiguration.setKeyAuth("keyAuth");

    chatConfiguration = new ChatConfiguration();
    chatConfiguration.setSharedPrivateKey(new Key("-----sharedPrivateKey"));

    authenticationService = mock(AuthenticationService.class);
    federatedAccountRepository = mock(FederatedAccountRepository.class);
    streamService = mock(StreamService.class);


    userSession = new UserSession("username", "jwt", "kmToken", "sessionToken");
    when(authenticationService.authenticate(anyString(), anyString(), anyString(), anyString())).thenReturn(userSession);

    symphonyMessageService = new SymphonyMessageService(podConfiguration, chatConfiguration, authenticationService, federatedAccountRepository, streamService);
  }

  @Test
  void sendRawMessage() {
    FederatedAccount federatedAccount = FederatedAccount.builder()
      .symphonyUsername("username")
      .build();
    when(federatedAccountRepository.findBySymphonyId("fromSymphonyUserId")).thenReturn(Optional.of(federatedAccount));

    symphonyMessageService.sendRawMessage("streamId", "fromSymphonyUserId", "text");

    verify(streamService, once()).sendMessage(podConfiguration.getUrl(), "streamId", "text", userSession);
  }

  @Test
  void sendRawMessage_FromSymphonyUserNotFound() {
    when(federatedAccountRepository.findBySymphonyId("fromSymphonyUserId")).thenReturn(Optional.empty());
    assertThrows(SendMessageFailedProblem.class, () -> symphonyMessageService.sendRawMessage("streamId", "fromSymphonyUserId", "text"));
  }
}
