package com.symphony.sfs.ms.chat.service;

import com.symphony.oss.models.chat.canon.UserEntity;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.oss.models.chat.canon.facade.User;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import com.symphony.sfs.ms.chat.generated.model.CreateChannelFailedProblem;
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
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.symphony.sfs.ms.starter.testing.MockitoUtils.once;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ChannelServiceTest {


  private ChannelService channelService;


  private StreamService streamService;
  private SymphonyMessageService symphonyMessageService;
  private PodConfiguration podConfiguration;
  private EmpClient empClient;
  private DatafeedSessionPool datafeedSessionPool;
  private FederatedAccountRepository federatedAccountRepository;

  private BotConfiguration botConfiguration;
  private AuthenticationService authenticationService;
  private UserSession userSession;

  @BeforeEach
  public void setUp() {

    streamService = mock(StreamService.class);
    symphonyMessageService = mock(SymphonyMessageService.class);
    empClient = mock(EmpClient.class);
    datafeedSessionPool = mock(DatafeedSessionPool.class);
    federatedAccountRepository = mock(FederatedAccountRepository.class);

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

    channelService = new ChannelService(streamService, symphonyMessageService, podConfiguration, empClient, mock(ForwarderQueueConsumer.class), datafeedSessionPool, federatedAccountRepository);
  }

  @Test
  void createIMChannel_FromSymphonyUser_ToFederatedUser() throws UnknownDatafeedUserException {

    FederatedAccount toFederatedAccount = newFederatedAccount("emp1", "101");
    IUser fromSymphonyUser = newIUser("1");

    when(empClient.createChannel("emp1", "streamId", Arrays.asList(toFederatedAccount), "1", Arrays.asList(fromSymphonyUser))).thenReturn(Optional.of("operationId"));

    DatafeedSessionPool.DatafeedSession userSession101 = new DatafeedSessionPool.DatafeedSession(userSession, "101");
    when(datafeedSessionPool.refreshSession("101")).thenReturn(userSession101);
    doNothing().when(symphonyMessageService).sendInfoMessage(any(UserSession.class), eq("streamId"), anyString());

    String streamId = channelService.createIMChannel(
      "streamId",
      newIUser("1"),
      toFederatedAccount
    );

    assertEquals("streamId", streamId);

    verify(datafeedSessionPool, once()).refreshSession("101");
    verify(empClient, once()).createChannel("emp1", "streamId", Arrays.asList(toFederatedAccount), "1", Arrays.asList(fromSymphonyUser));
    verify(symphonyMessageService, once()).sendInfoMessage(userSession101, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");

    verifyNoMoreInteractions(datafeedSessionPool, empClient, symphonyMessageService);

  }

  @Test
  void createMIMChannel() throws UnknownDatafeedUserException {

    List<FederatedAccount> federatedAccountsForEmp1 = Arrays.asList(newFederatedAccount("emp1", "101"), newFederatedAccount("emp1", "102"));
    List<FederatedAccount> federatedAccountsForEmp2 = Arrays.asList(newFederatedAccount("emp2", "201"), newFederatedAccount("emp2", "202"));
    List<IUser> symphonyUsers = Arrays.asList(newIUser("2"), newIUser("3"));

    when(empClient.createChannel("emp1", "streamId", federatedAccountsForEmp1, "1", symphonyUsers)).thenReturn(Optional.of("operationId1"));
    when(empClient.createChannel("emp2", "streamId", federatedAccountsForEmp2, "1", symphonyUsers)).thenReturn(Optional.of("operationId2"));
    doNothing().when(symphonyMessageService).sendInfoMessage(any(UserSession.class), eq("streamId"), anyString());

    DatafeedSessionPool.DatafeedSession userSession101 = new DatafeedSessionPool.DatafeedSession(userSession, "101");
    when(datafeedSessionPool.refreshSession("101")).thenReturn(userSession101);
    DatafeedSessionPool.DatafeedSession userSession102 = new DatafeedSessionPool.DatafeedSession(userSession, "102");
    when(datafeedSessionPool.refreshSession("102")).thenReturn(userSession102);
    DatafeedSessionPool.DatafeedSession userSession201 = new DatafeedSessionPool.DatafeedSession(userSession, "201");
    when(datafeedSessionPool.refreshSession("201")).thenReturn(userSession201);
    DatafeedSessionPool.DatafeedSession userSession202 = new DatafeedSessionPool.DatafeedSession(userSession, "202");
    when(datafeedSessionPool.refreshSession("202")).thenReturn(userSession202);

    String streamId = channelService.createMIMChannel(
      "streamId",
      newIUser("1"),
      Stream.of(federatedAccountsForEmp1, federatedAccountsForEmp2).collect(toMap(list -> list.get(0).getEmp(), Function.identity(), (existingValue, replacementValue) -> existingValue, LinkedHashMap::new)),
      symphonyUsers);

    assertEquals("streamId", streamId);

    InOrder orderVerifier = inOrder(datafeedSessionPool, empClient, symphonyMessageService);

    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("101");
    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("102");
    orderVerifier.verify(empClient, once()).createChannel("emp1", "streamId", federatedAccountsForEmp1, "1", symphonyUsers);
    orderVerifier.verify(symphonyMessageService, once()).sendInfoMessage(userSession101, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
    orderVerifier.verify(symphonyMessageService, once()).sendInfoMessage(userSession102, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");

    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("201");
    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("202");
    orderVerifier.verify(empClient, once()).createChannel("emp2", "streamId", federatedAccountsForEmp2, "1", symphonyUsers);
    orderVerifier.verify(symphonyMessageService, once()).sendInfoMessage(userSession201, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
    orderVerifier.verify(symphonyMessageService, once()).sendInfoMessage(userSession202, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");

    orderVerifier.verifyNoMoreInteractions();
  }

  @Test
  void createMIMChannel_UnknownDatafeed() throws UnknownDatafeedUserException {

    List<FederatedAccount> federatedAccountsForEmp1 = Arrays.asList(newFederatedAccount("emp1", "101"), newFederatedAccount("emp1", "102"));
    List<FederatedAccount> federatedAccountsForEmp2 = Arrays.asList(newFederatedAccount("emp2", "201"), newFederatedAccount("emp2", "202"));
    List<IUser> symphonyUsers = Arrays.asList(newIUser("2"), newIUser("3"));

    when(empClient.createChannel("emp1", "streamId", federatedAccountsForEmp1, "1", symphonyUsers)).thenReturn(Optional.of("operationId1"));
    when(empClient.createChannel("emp2", "streamId", federatedAccountsForEmp2, "1", symphonyUsers)).thenReturn(Optional.of("operationId2"));

    DatafeedSessionPool.DatafeedSession userSession101 = new DatafeedSessionPool.DatafeedSession(userSession, "101");
    when(datafeedSessionPool.refreshSession("101")).thenReturn(userSession101);
    DatafeedSessionPool.DatafeedSession userSession102 = new DatafeedSessionPool.DatafeedSession(userSession, "102");
    when(datafeedSessionPool.refreshSession("102")).thenReturn(userSession102);
    doNothing().when(symphonyMessageService).sendInfoMessage(any(UserSession.class), eq("streamId"), anyString());
    DatafeedSessionPool.DatafeedSession userSession201 = new DatafeedSessionPool.DatafeedSession(userSession, "201");
    when(datafeedSessionPool.refreshSession("201")).thenThrow(UnknownDatafeedUserException.class);
    DatafeedSessionPool.DatafeedSession userSession202 = new DatafeedSessionPool.DatafeedSession(userSession, "202");
    when(datafeedSessionPool.refreshSession("202")).thenReturn(userSession202);

    IllegalStateException exception = assertThrows(IllegalStateException.class, () -> channelService.createMIMChannel(
      "streamId",
      newIUser("1"),
      // Use a LinkedHashMap to have predictable test results
      Stream.of(federatedAccountsForEmp1, federatedAccountsForEmp2).collect(toMap(list -> list.get(0).getEmp(), Function.identity(), (existingValue, replacementValue) -> existingValue, LinkedHashMap::new)),
      symphonyUsers));


    assertEquals(UnknownDatafeedUserException.class, exception.getCause().getClass());

    InOrder orderVerifier = inOrder(datafeedSessionPool, empClient, symphonyMessageService);

    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("101");
    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("102");
    orderVerifier.verify(empClient, once()).createChannel("emp1", "streamId", federatedAccountsForEmp1, "1", symphonyUsers);
    orderVerifier.verify(symphonyMessageService, once()).sendInfoMessage(userSession101, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
    orderVerifier.verify(symphonyMessageService, once()).sendInfoMessage(userSession102, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");

    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("201");

    // Because of the problem with the userSession201, we have no other interactions
    // emp2 is not called but emp1 has been called
    orderVerifier.verify(datafeedSessionPool, never()).refreshSession("202");
    orderVerifier.verify(empClient, never()).createChannel("emp2", "streamId", federatedAccountsForEmp2, "1", symphonyUsers);
    orderVerifier.verify(symphonyMessageService, never()).sendInfoMessage(userSession201, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
    orderVerifier.verify(symphonyMessageService, never()).sendInfoMessage(userSession202, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");

    orderVerifier.verifyNoMoreInteractions();
  }

  @Test
  void createMIMChannel_ProblemWithOneEMP() throws UnknownDatafeedUserException {

    List<FederatedAccount> federatedAccountsForEmp1 = Arrays.asList(newFederatedAccount("emp1", "101"), newFederatedAccount("emp1", "102"));
    List<FederatedAccount> federatedAccountsForEmp2 = Arrays.asList(newFederatedAccount("emp2", "201"), newFederatedAccount("emp2", "202"));
    List<FederatedAccount> federatedAccountsForEmp3 = Arrays.asList(newFederatedAccount("emp3", "301"), newFederatedAccount("emp3", "302"));
    List<IUser> symphonyUsers = Arrays.asList(newIUser("2"), newIUser("3"));

    when(empClient.createChannel("emp1", "streamId", federatedAccountsForEmp1, "1", symphonyUsers)).thenReturn(Optional.of("operationId1"));
    when(empClient.createChannel("emp2", "streamId", federatedAccountsForEmp2, "1", symphonyUsers)).thenThrow(CreateChannelFailedProblem.class);
    when(empClient.createChannel("emp3", "streamId", federatedAccountsForEmp3, "1", symphonyUsers)).thenReturn(Optional.of("operationId3"));

    DatafeedSessionPool.DatafeedSession userSession101 = new DatafeedSessionPool.DatafeedSession(userSession, "101");
    when(datafeedSessionPool.refreshSession("101")).thenReturn(userSession101);
    DatafeedSessionPool.DatafeedSession userSession102 = new DatafeedSessionPool.DatafeedSession(userSession, "102");
    when(datafeedSessionPool.refreshSession("102")).thenReturn(userSession102);
    doNothing().when(symphonyMessageService).sendInfoMessage(any(UserSession.class), eq("streamId"), anyString());
    DatafeedSessionPool.DatafeedSession userSession201 = new DatafeedSessionPool.DatafeedSession(userSession, "201");
    when(datafeedSessionPool.refreshSession("201")).thenReturn(userSession201);
    DatafeedSessionPool.DatafeedSession userSession202 = new DatafeedSessionPool.DatafeedSession(userSession, "202");
    when(datafeedSessionPool.refreshSession("202")).thenReturn(userSession202);
    DatafeedSessionPool.DatafeedSession userSession301 = new DatafeedSessionPool.DatafeedSession(userSession, "301");
    when(datafeedSessionPool.refreshSession("301")).thenReturn(userSession301);
    DatafeedSessionPool.DatafeedSession userSession302 = new DatafeedSessionPool.DatafeedSession(userSession, "302");
    when(datafeedSessionPool.refreshSession("302")).thenReturn(userSession302);

    assertThrows(CreateChannelFailedProblem.class, () -> channelService.createMIMChannel(
      "streamId",
      newIUser("1"),
      // Use a LinkedHashMap to have predictable test results
      Stream.of(federatedAccountsForEmp1, federatedAccountsForEmp2).collect(toMap(list -> list.get(0).getEmp(), Function.identity(), (existingValue, replacementValue) -> existingValue, LinkedHashMap::new)),
      symphonyUsers));

    InOrder orderVerifier = inOrder(datafeedSessionPool, empClient, symphonyMessageService);

    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("101");
    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("102");
    orderVerifier.verify(empClient, once()).createChannel("emp1", "streamId", federatedAccountsForEmp1, "1", symphonyUsers);
    orderVerifier.verify(symphonyMessageService, once()).sendInfoMessage(userSession101, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
    orderVerifier.verify(symphonyMessageService, once()).sendInfoMessage(userSession102, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");


    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("201");
    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("202");
    orderVerifier.verify(empClient, once()).createChannel("emp2", "streamId", federatedAccountsForEmp2, "1", symphonyUsers);

    // Because of the problem with the emp2 we have no other interaction
    // emp1 has been called but emp2 and emp3 have not been called
    orderVerifier.verify(symphonyMessageService, never()).sendInfoMessage(userSession201, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
    orderVerifier.verify(symphonyMessageService, never()).sendInfoMessage(userSession202, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
    orderVerifier.verify(datafeedSessionPool, never()).refreshSession("301");
    orderVerifier.verify(datafeedSessionPool, never()).refreshSession("302");
    orderVerifier.verify(empClient, never()).createChannel("emp3", "streamId", federatedAccountsForEmp3, "1", symphonyUsers);
    orderVerifier.verify(symphonyMessageService, never()).sendInfoMessage(userSession301, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
    orderVerifier.verify(symphonyMessageService, never()).sendInfoMessage(userSession302, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");

    orderVerifier.verifyNoMoreInteractions();
  }

  private IUser newIUser(String symphonyId) {
    UserEntity.Builder builder = new UserEntity.Builder()
      .withId(Long.valueOf(symphonyId))
      .withFirstName(symphonyId + "_firstName")
      .withSurname(symphonyId + "_lastName");
    return new User(builder);
  }

  private FederatedAccount newFederatedAccount(String emp, String symphonyUserId) {
    return FederatedAccount.builder()
      .emp(emp)
      .symphonyUserId(symphonyUserId)
      .build();
  }
}
