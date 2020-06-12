package com.symphony.sfs.ms.chat.service;

import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.oss.models.core.canon.facade.PodAndUserId;
import com.symphony.sfs.ms.admin.generated.model.EntitlementResponse;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.MessageService;
import com.symphony.sfs.ms.chat.service.SymphonyMessageService;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.config.properties.common.Key;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.UserSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.symphony.sfs.ms.starter.testing.MockitoUtils.once;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class MessageServiceTest {

  private MessageService messageService;

  private EmpClient empClient;
  private AuthenticationService authenticationService;
  private PodConfiguration podConfiguration;
  private BotConfiguration botConfiguration;
  private FederatedAccountRepository federatedAccountRepository;
  private DatafeedSessionPool datafeedSessionPool;
  private SymphonyMessageService symphonyMessageService;
  private AdminClient adminClient;

  private UserSession userSession;

  @BeforeEach
  public void setUp() {

    empClient = mock(EmpClient.class);
    federatedAccountRepository = mock(FederatedAccountRepository.class);
    datafeedSessionPool = mock(DatafeedSessionPool.class);
    authenticationService = mock(AuthenticationService.class);

    botConfiguration = new BotConfiguration();
    botConfiguration.setUsername("username");
    botConfiguration.setEmailAddress("emailAddress");
    botConfiguration.setPrivateKey(new Key("-----botConfigurationPrivateKey"));

    podConfiguration = new PodConfiguration();
    podConfiguration.setUrl("podUrl");
    podConfiguration.setSessionAuth("sessionAuth");
    podConfiguration.setKeyAuth("keyAuth");

    symphonyMessageService = mock(SymphonyMessageService.class);

    userSession = new UserSession("username", "jwt", "kmToken", "sessionToken");
    when(authenticationService.authenticate(anyString(), anyString(), anyString(), anyString())).thenReturn(userSession);

    adminClient = mock(AdminClient.class);

    messageService = new MessageService(empClient, federatedAccountRepository, mock(ForwarderQueueConsumer.class), datafeedSessionPool, symphonyMessageService, adminClient);
  }

  @Test
  public void testMessageWithDisclaimer() {
    IUser fromSymphonyUser = newIUser("1");
    List<String> members = Arrays.asList("1", "101");
    FederatedAccount federatedAccount101 =  newFederatedAccount("emp", "101");

    when(federatedAccountRepository.findBySymphonyId("101")).thenReturn(Optional.of(federatedAccount101));
    // fromSymphonyUser is advisor
    when(adminClient.getEntitlementAccess("1", "emp")).thenReturn(Optional.of(new EntitlementResponse()));

    long now  = new Date().getTime();


    // Without disclaimer
    messageService.onIMMessage("streamId", "messageId", fromSymphonyUser, members, now, "message", null);

    InOrder orderVerifier =  inOrder(empClient);
    orderVerifier.verify(empClient, never()).sendMessage("emp", "streamId", "messageId", fromSymphonyUser, Collections.singletonList(federatedAccount101), now, "disclaimer");
    orderVerifier.verify(empClient, once()).sendMessage("emp", "streamId", "messageId", fromSymphonyUser, Collections.singletonList(federatedAccount101), now, "message");
    orderVerifier.verifyNoMoreInteractions();

    // With disclaimer
    messageService.onIMMessage("streamId", "messageId", fromSymphonyUser, members, now, "message", "disclaimer");

    orderVerifier.verify(empClient, once()).sendMessage("emp", "streamId", "messageId", fromSymphonyUser, Collections.singletonList(federatedAccount101), now, "disclaimer");
    orderVerifier.verify(empClient, once()).sendMessage("emp", "streamId", "messageId", fromSymphonyUser, Collections.singletonList(federatedAccount101), now, "message");
    orderVerifier.verifyNoMoreInteractions();
  }
//
//  @Test
//  void onIMMessage() {
//
//    FederatedAccount toFederatedAccount = FederatedAccount.builder()
//      .emp("emp")
//      .symphonyUserId("234567891")
//      .build();
//
//    IUser fromSymphonyUser = newIUser("123456789");
//
//    long now = OffsetDateTime.now().toEpochSecond();
//
//
//    when(federatedAccountRepository.findBySymphonyId("234567891")).thenReturn(Optional.of(toFederatedAccount));
//    when(empClient.sendMessage("emp", "streamId", "messageId", fromSymphonyUser, toFederatedAccount, now, "text")).thenReturn(Optional.of("leaseId"));
//    messageService.onIMMessage("streamId", "messageId", fromSymphonyUser, Arrays.asList("123456789", "234567891"), now, "text");
//
//    verify(federatedAccountRepository, once()).findBySymphonyId("123456789");
//    verify(federatedAccountRepository, once()).findBySymphonyId("234567891");
//    verify(empClient, once()).sendMessage("emp", "streamId", "messageId", fromSymphonyUser, Collections.singletonList(toFederatedAccount), now, "text");
//  }
//
//  @Test
//  void onIMMessage_FederatedServiceAccountNotFound() {
//
//    IUser fromSymphonyUser = newIUser("123456789");
//    long now = OffsetDateTime.now().toEpochSecond();
//
//    when(federatedAccountRepository.findBySymphonyId("123456789")).thenReturn(Optional.empty());
//    assertThrows(FederatedAccountNotFoundProblem.class, () -> messageService.onIMMessage("streamId", "messageId", fromSymphonyUser, Arrays.asList("123456789", "234567891"), now, "text"));
//  }
//
//  @Test
//  void onMIMMessage_FromSymphony_ToEMPs() throws UnknownDatafeedUserException {
//
//    FederatedAccount federatedAccount101 =  newFederatedAccount("emp1", "101");
//    FederatedAccount federatedAccount102 =  newFederatedAccount("emp1", "102");
//    FederatedAccount federatedAccount201 =  newFederatedAccount("emp2", "201");
//    FederatedAccount federatedAccount202 =  newFederatedAccount("emp2", "202");
//
//    List<FederatedAccount> federatedAccountsForEmp1 = Arrays.asList(federatedAccount101, federatedAccount102);
//    List<FederatedAccount> federatedAccountsForEmp2 = Arrays.asList(federatedAccount201, federatedAccount202);
//
//    List<String> mimMembers = Arrays.asList("1", "2", "3", "101", "102", "201", "202");
////    List<IUser> mimMembers = Arrays.asList(newIUser("1"), newIUser("2"), newIUser("101"), newIUser("102"), newIUser("201"), newIUser("202"));
//    IUser fromSymphonyUser = newIUser("1");
//
//    long now = OffsetDateTime.now().toEpochSecond();
//
//    // Messsage sender is not a federated user --> messaging from Symphony to EMPs
//    when(federatedAccountRepository.findBySymphonyId(fromSymphonyUser.getId().toString())).thenReturn(Optional.empty());
//
//    // FederatedAccounts
//    when(federatedAccountRepository.findBySymphonyId("101")).thenReturn(Optional.of(federatedAccount101));
//    when(federatedAccountRepository.findBySymphonyId("102")).thenReturn(Optional.of(federatedAccount102));
//    when(federatedAccountRepository.findBySymphonyId("201")).thenReturn(Optional.of(federatedAccount201));
//    when(federatedAccountRepository.findBySymphonyId("202")).thenReturn(Optional.of(federatedAccount202));
//
//    // Non Federated Accounts
//    when(federatedAccountRepository.findBySymphonyId("2")).thenReturn(Optional.empty());
//    when(federatedAccountRepository.findBySymphonyId("3")).thenReturn(Optional.empty());
//
//
//    when(empClient.sendMessage("emp1", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp1, now, "text")).thenReturn(Optional.of("lease1"));
//    when(empClient.sendMessage("emp2", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp2, now, "text")).thenReturn(Optional.of("lease2"));
//
//    DatafeedSessionPool.DatafeedSession userSession101 = new DatafeedSessionPool.DatafeedSession(userSession, "101");
//    when(datafeedSessionPool.refreshSession("101")).thenReturn(userSession101);
//    DatafeedSessionPool.DatafeedSession userSession102 = new DatafeedSessionPool.DatafeedSession(userSession, "102");
//    when(datafeedSessionPool.refreshSession("102")).thenReturn(userSession102);
//    DatafeedSessionPool.DatafeedSession userSession201 = new DatafeedSessionPool.DatafeedSession(userSession, "201");
//    when(datafeedSessionPool.refreshSession("201")).thenReturn(userSession201);
//    DatafeedSessionPool.DatafeedSession userSession202 = new DatafeedSessionPool.DatafeedSession(userSession, "202");
//    when(datafeedSessionPool.refreshSession("202")).thenReturn(userSession202);
//
//    messageService.onIMMessage("streamId", "messageId", fromSymphonyUser, mimMembers, now, "text");
//
//    InOrder orderVerifier = inOrder(federatedAccountRepository, datafeedSessionPool, empClient);
//
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("1");
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("2");
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("3");
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("101");
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("102");
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("201");
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("202");
//
//
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("101");
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("102");
//    orderVerifier.verify(empClient, once()).sendMessage("emp1", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp1, now, "text");
//
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("201");
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("202");
//    orderVerifier.verify(empClient, once()).sendMessage("emp2", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp2, now, "text");
//
//    orderVerifier.verifyNoMoreInteractions();
//  }
//
//  @Test
//  void onMIMMessage_FederatedServiceAccountNotFound() {
//
//    IUser fromSymphonyUser = newIUser("123456789");
//    long now = OffsetDateTime.now().toEpochSecond();
//
//    // Do not find any FederatedAccount
//    when(federatedAccountRepository.findBySymphonyId(anyString())).thenReturn(Optional.empty());
//    assertThrows(FederatedAccountNotFoundProblem.class, () -> messageService.onIMMessage("streamId", "messageId", fromSymphonyUser, Arrays.asList("123456789", "234567891", "345678912", "456789123"), now, "text"));
//  }
//
//  @Test
//  void onMIMMessage_FromSymphony_ToEMPs_UnknownDatafeed() throws UnknownDatafeedUserException {
//
//    FederatedAccount federatedAccount101 =  newFederatedAccount("emp1", "101");
//    FederatedAccount federatedAccount102 =  newFederatedAccount("emp1", "102");
//    FederatedAccount federatedAccount201 =  newFederatedAccount("emp2", "201");
//    FederatedAccount federatedAccount202 =  newFederatedAccount("emp2", "202");
//
//    List<FederatedAccount> federatedAccountsForEmp1 = Arrays.asList(federatedAccount101, federatedAccount102);
//    List<FederatedAccount> federatedAccountsForEmp2 = Arrays.asList(federatedAccount201, federatedAccount202);
//
//    // Contains everyone: sender, federated and non-federated users
//    List<String> mimMembers = Arrays.asList("1", "2", "3", "101", "102", "201", "202");
//    IUser fromSymphonyUser = newIUser("1");
//
//    long now = OffsetDateTime.now().toEpochSecond();
//
//    // Messsage sender is not a federated user --> messaging from Symphony to EMPs
//    when(federatedAccountRepository.findBySymphonyId(fromSymphonyUser.getId().toString())).thenReturn(Optional.empty());
//
//    // FederatedAccounts
//    when(federatedAccountRepository.findBySymphonyId("101")).thenReturn(Optional.of(federatedAccount101));
//    when(federatedAccountRepository.findBySymphonyId("102")).thenReturn(Optional.of(federatedAccount102));
//    when(federatedAccountRepository.findBySymphonyId("201")).thenReturn(Optional.of(federatedAccount201));
//    when(federatedAccountRepository.findBySymphonyId("202")).thenReturn(Optional.of(federatedAccount202));
//
//    // Non Federated Accounts
//    when(federatedAccountRepository.findBySymphonyId("2")).thenReturn(Optional.empty());
//    when(federatedAccountRepository.findBySymphonyId("3")).thenReturn(Optional.empty());
//
//
//    when(empClient.sendMessage("emp1", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp1, now, "text")).thenReturn(Optional.of("lease1"));
//    when(empClient.sendMessage("emp2", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp2, now, "text")).thenReturn(Optional.of("lease2"));
//
//    DatafeedSessionPool.DatafeedSession userSession101 = new DatafeedSessionPool.DatafeedSession(userSession, "101");
//    when(datafeedSessionPool.refreshSession("101")).thenReturn(userSession101);
//    DatafeedSessionPool.DatafeedSession userSession102 = new DatafeedSessionPool.DatafeedSession(userSession, "102");
//    when(datafeedSessionPool.refreshSession("102")).thenReturn(userSession102);
//    DatafeedSessionPool.DatafeedSession userSession201 = new DatafeedSessionPool.DatafeedSession(userSession, "201");
//    // userSession201 is unknown
//    when(datafeedSessionPool.refreshSession("201")).thenThrow(UnknownDatafeedUserException.class);
//    DatafeedSessionPool.DatafeedSession userSession202 = new DatafeedSessionPool.DatafeedSession(userSession, "202");
//    when(datafeedSessionPool.refreshSession("202")).thenReturn(userSession202);
//
//    IllegalStateException exception = assertThrows(IllegalStateException.class, () -> messageService.onIMMessage("streamId", "messageId", fromSymphonyUser, mimMembers, now, "text"));
//    assertEquals(UnknownDatafeedUserException.class, exception.getCause().getClass());
//
//    InOrder orderVerifier = inOrder(federatedAccountRepository, datafeedSessionPool, empClient);
//
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("1");
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("2");
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("3");
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("101");
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("102");
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("201");
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("202");
//
//
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("101");
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("102");
//    orderVerifier.verify(empClient, once()).sendMessage("emp1", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp1, now, "text");
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("201");
//
//    // UserSession201 error -> whole process is aborted
//    orderVerifier.verify(datafeedSessionPool, never()).refreshSession("202");
//    orderVerifier.verify(empClient, never()).sendMessage("emp2", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp2, now, "text");
//
//    orderVerifier.verifyNoMoreInteractions();
//  }
//
//  @Test
//  void onMIMMessage_ProblemWithOneEMP() throws UnknownDatafeedUserException {
//
//    FederatedAccount federatedAccount101 =  newFederatedAccount("emp1", "101");
//    FederatedAccount federatedAccount102 =  newFederatedAccount("emp1", "102");
//    FederatedAccount federatedAccount201 =  newFederatedAccount("emp2", "201");
//    FederatedAccount federatedAccount202 =  newFederatedAccount("emp2", "202");
//    FederatedAccount federatedAccount301 =  newFederatedAccount("emp3", "301");
//    FederatedAccount federatedAccount302 =  newFederatedAccount("emp3", "302");
//
//    List<FederatedAccount> federatedAccountsForEmp1 = Arrays.asList(federatedAccount101, federatedAccount102);
//    List<FederatedAccount> federatedAccountsForEmp2 = Arrays.asList(federatedAccount201, federatedAccount202);
//    List<FederatedAccount> federatedAccountsForEmp3 = Arrays.asList(federatedAccount301, federatedAccount302);
//
//    // Contains everyone: sender, federated and non-federated users
//    List<String> mimMembers = Arrays.asList("1", "2", "3", "101", "102", "201", "202", "301", "302");
//    IUser fromSymphonyUser = newIUser("1");
//
//    long now = OffsetDateTime.now().toEpochSecond();
//
//    // Messsage sender is not a federated user --> messaging from Symphony to EMPs
//    when(federatedAccountRepository.findBySymphonyId(fromSymphonyUser.getId().toString())).thenReturn(Optional.empty());
//
//    // FederatedAccounts
//    when(federatedAccountRepository.findBySymphonyId("101")).thenReturn(Optional.of(federatedAccount101));
//    when(federatedAccountRepository.findBySymphonyId("102")).thenReturn(Optional.of(federatedAccount102));
//    when(federatedAccountRepository.findBySymphonyId("201")).thenReturn(Optional.of(federatedAccount201));
//    when(federatedAccountRepository.findBySymphonyId("202")).thenReturn(Optional.of(federatedAccount202));
//    when(federatedAccountRepository.findBySymphonyId("301")).thenReturn(Optional.of(federatedAccount301));
//    when(federatedAccountRepository.findBySymphonyId("302")).thenReturn(Optional.of(federatedAccount302));
//
//    // Non Federated Accounts
//    when(federatedAccountRepository.findBySymphonyId("2")).thenReturn(Optional.empty());
//    when(federatedAccountRepository.findBySymphonyId("3")).thenReturn(Optional.empty());
//
//
//    when(empClient.sendMessage("emp1", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp1, now, "text")).thenReturn(Optional.of("lease1"));
//    when(empClient.sendMessage("emp2", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp2, now, "text")).thenThrow(RuntimeException.class);
//    when(empClient.sendMessage("emp3", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp3, now, "text")).thenReturn(Optional.of("lease3"));
//
//    DatafeedSessionPool.DatafeedSession userSession101 = new DatafeedSessionPool.DatafeedSession(userSession, "101");
//    when(datafeedSessionPool.refreshSession("101")).thenReturn(userSession101);
//    DatafeedSessionPool.DatafeedSession userSession102 = new DatafeedSessionPool.DatafeedSession(userSession, "102");
//    when(datafeedSessionPool.refreshSession("102")).thenReturn(userSession102);
//    DatafeedSessionPool.DatafeedSession userSession201 = new DatafeedSessionPool.DatafeedSession(userSession, "201");
//    when(datafeedSessionPool.refreshSession("201")).thenReturn(userSession201);
//    DatafeedSessionPool.DatafeedSession userSession202 = new DatafeedSessionPool.DatafeedSession(userSession, "202");
//    when(datafeedSessionPool.refreshSession("202")).thenReturn(userSession202);
//    DatafeedSessionPool.DatafeedSession userSession301 = new DatafeedSessionPool.DatafeedSession(userSession, "301");
//    when(datafeedSessionPool.refreshSession("301")).thenReturn (userSession301);
//    DatafeedSessionPool.DatafeedSession userSession302 = new DatafeedSessionPool.DatafeedSession(userSession, "302");
//    when(datafeedSessionPool.refreshSession("302")).thenReturn(userSession302);
//
//    RuntimeException exception = assertThrows(RuntimeException.class, () -> messageService.onIMMessage("streamId", "messageId", fromSymphonyUser, mimMembers, now, "text"));
//
//    InOrder orderVerifier = inOrder(federatedAccountRepository, datafeedSessionPool, empClient);
//
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("1");
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("2");
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("3");
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("101");
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("102");
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("201");
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("202");
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("301");
//    orderVerifier.verify(federatedAccountRepository, once()).findBySymphonyId("302");
//
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("101");
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("102");
//    orderVerifier.verify(empClient, once()).sendMessage("emp1", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp1, now, "text");
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("201");
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("202");
//    orderVerifier.verify(empClient, once()).sendMessage("emp2", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp2, now, "text");
//    // No interaction from here
//    orderVerifier.verify(datafeedSessionPool, never()).refreshSession("301");
//    orderVerifier.verify(datafeedSessionPool, never()).refreshSession("302");
//    orderVerifier.verify(empClient, never()).sendMessage("emp3", "streamId", "messageId", fromSymphonyUser, federatedAccountsForEmp3, now, "text");
//
//    orderVerifier.verifyNoMoreInteractions();
//  }
//
  private IUser newIUser(String symphonyUserId) {

    PodAndUserId id = PodAndUserId.newBuilder().build(Long.valueOf(symphonyUserId));
    IUser mockIUser = mock(IUser.class);
    when(mockIUser.getId()).thenReturn(id);

    return mockIUser;

  }

  private FederatedAccount newFederatedAccount(String emp, String symphonyUserId) {
    return FederatedAccount.builder()
      .emp(emp)
      .symphonyUserId(symphonyUserId)
      .build();
  }
}