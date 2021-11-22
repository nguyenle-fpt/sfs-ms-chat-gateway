package com.symphony.sfs.ms.chat.service;

import com.symphony.oss.models.chat.canon.UserEntity;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.oss.models.chat.canon.facade.User;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.ChannelRepository;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyService;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.config.properties.common.PemResource;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.testing.I18nTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.symphony.sfs.ms.starter.testing.MockitoUtils.once;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelServiceTest implements I18nTest {

  private ChannelService channelService;

  private SymphonyMessageSender symphonyMessageSender;
  private EmpClient empClient;
  private DatafeedSessionPool datafeedSessionPool;
  private FederatedAccountRepository federatedAccountRepository;

  private SymphonySession userSession;
  private AdminClient mockAdminClient;
  private StreamService streamService;
  private EmpSchemaService empSchemaService;

  private SymphonySession botSession;

  private static final String STREAM_ID_1 = "streamId";
  private static final String EMP_1 = "emp_1";

  @BeforeEach
  public void setUp(MessageSource messageSource) {
    mockAdminClient = mock(AdminClient.class);
    SymphonyService symphonyService = mock(SymphonyService.class);
    streamService = mock(StreamService.class);
    symphonyMessageSender = mock(SymphonyMessageSender.class);
    empClient = mock(EmpClient.class);
    datafeedSessionPool = mock(DatafeedSessionPool.class);
    federatedAccountRepository = mock(FederatedAccountRepository.class);
    ChannelRepository channelRepository = mock(ChannelRepository.class);
    AuthenticationService authenticationService = mock(AuthenticationService.class);

    BotConfiguration botConfiguration = new BotConfiguration();
    botConfiguration.setUsername("username");
    botConfiguration.setEmailAddress("emailAddress");
    botConfiguration.setPrivateKey(new PemResource("-----botConfigurationPrivateKey"));

    PodConfiguration podConfiguration = new PodConfiguration();
    podConfiguration.setUrl("podUrl");
    podConfiguration.setSessionAuth("sessionAuth");
    podConfiguration.setKeyAuth("keyAuth");


    userSession = new SymphonySession("username", "kmToken", "sessionToken");
    when(authenticationService.authenticate(anyString(), anyString(), anyString(), anyString())).thenReturn(userSession);

    empSchemaService =  mock(EmpSchemaService.class);

    channelService = new ChannelService(symphonyMessageSender, empClient, mock(ForwarderQueueConsumer.class), datafeedSessionPool, federatedAccountRepository, empSchemaService, channelRepository, messageSource);

    botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());
  }


  @Test
  void onCreateIM_IM_deprecated() throws UnknownDatafeedUserException {
    FederatedAccount toFederatedAccount = newFederatedAccount("emp1", "101");
    IUser fromSymphonyUser = newIUser("1");

    when(federatedAccountRepository.findBySymphonyId("101")).thenReturn(Optional.of(toFederatedAccount));
    DatafeedSessionPool.DatafeedSession userSession101 = new DatafeedSessionPool.DatafeedSession(userSession, "101");
    when(datafeedSessionPool.refreshSession("101")).thenReturn(userSession101);
    when(empSchemaService.getEmpDisplayName("emp1")).thenReturn("External Messaging Platform");

    List<String> members = new ArrayList<>();
    members.add("1");
    members.add("101");
    channelService.onIMCreated("streamId", members, fromSymphonyUser, false);
    verify(symphonyMessageSender, once()).sendAlertMessage(userSession101, "streamId", "Sorry, we're not able to start this conversation. Please ask your administrator for assistance.", Collections.emptyList());
  }


//  @Test
//  void createMIMChannel() throws UnknownDatafeedUserException {
//
//    List<FederatedAccount> federatedAccountsForEmp1 = Arrays.asList(newFederatedAccount("emp1", "101"), newFederatedAccount("emp1", "102"));
//    List<FederatedAccount> federatedAccountsForEmp2 = Arrays.asList(newFederatedAccount("emp2", "201"), newFederatedAccount("emp2", "202"));
//    List<IUser> symphonyUsers = Arrays.asList(newIUser("2"), newIUser("3"));
//
//    when(empClient.createChannel("emp1", "streamId", federatedAccountsForEmp1, "1", symphonyUsers)).thenReturn(Optional.of("operationId1"));
//    when(empClient.createChannel("emp2", "streamId", federatedAccountsForEmp2, "1", symphonyUsers)).thenReturn(Optional.of("operationId2"));
//    doNothing().when(symphonyMessageSender).sendInfoMessage(any(UserSession.class), eq("streamId"), anyString());
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
//    String streamId = channelService.createMIMChannel(
//      "streamId",
//      newIUser("1"),
//      Stream.of(federatedAccountsForEmp1, federatedAccountsForEmp2).collect(toMap(list -> list.get(0).getEmp(), Function.identity(), (existingValue, replacementValue) -> existingValue, LinkedHashMap::new)),
//      symphonyUsers);
//
//    assertEquals("streamId", streamId);
//
//    InOrder orderVerifier = inOrder(datafeedSessionPool, empClient, symphonyMessageSender);
//
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("101");
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("102");
//    orderVerifier.verify(empClient, once()).createChannel("emp1", "streamId", federatedAccountsForEmp1, "1", symphonyUsers);
//    orderVerifier.verify(symphonyMessageSender, once()).sendInfoMessage(userSession101, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
//    orderVerifier.verify(symphonyMessageSender, once()).sendInfoMessage(userSession102, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
//
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("201");
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("202");
//    orderVerifier.verify(empClient, once()).createChannel("emp2", "streamId", federatedAccountsForEmp2, "1", symphonyUsers);
//    orderVerifier.verify(symphonyMessageSender, once()).sendInfoMessage(userSession201, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
//    orderVerifier.verify(symphonyMessageSender, once()).sendInfoMessage(userSession202, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
//
//    orderVerifier.verifyNoMoreInteractions();
//  }
//
//  @Test
//  void createMIMChannel_UnknownDatafeed() throws UnknownDatafeedUserException {
//
//    List<FederatedAccount> federatedAccountsForEmp1 = Arrays.asList(newFederatedAccount("emp1", "101"), newFederatedAccount("emp1", "102"));
//    List<FederatedAccount> federatedAccountsForEmp2 = Arrays.asList(newFederatedAccount("emp2", "201"), newFederatedAccount("emp2", "202"));
//    List<IUser> symphonyUsers = Arrays.asList(newIUser("2"), newIUser("3"));
//
//    when(empClient.createChannel("emp1", "streamId", federatedAccountsForEmp1, "1", symphonyUsers)).thenReturn(Optional.of("operationId1"));
//    when(empClient.createChannel("emp2", "streamId", federatedAccountsForEmp2, "1", symphonyUsers)).thenReturn(Optional.of("operationId2"));
//
//    DatafeedSessionPool.DatafeedSession userSession101 = new DatafeedSessionPool.DatafeedSession(userSession, "101");
//    when(datafeedSessionPool.refreshSession("101")).thenReturn(userSession101);
//    DatafeedSessionPool.DatafeedSession userSession102 = new DatafeedSessionPool.DatafeedSession(userSession, "102");
//    when(datafeedSessionPool.refreshSession("102")).thenReturn(userSession102);
//    doNothing().when(symphonyMessageSender).sendInfoMessage(any(UserSession.class), eq("streamId"), anyString());
//    DatafeedSessionPool.DatafeedSession userSession201 = new DatafeedSessionPool.DatafeedSession(userSession, "201");
//    when(datafeedSessionPool.refreshSession("201")).thenThrow(UnknownDatafeedUserException.class);
//    DatafeedSessionPool.DatafeedSession userSession202 = new DatafeedSessionPool.DatafeedSession(userSession, "202");
//    when(datafeedSessionPool.refreshSession("202")).thenReturn(userSession202);
//
//    IllegalStateException exception = assertThrows(IllegalStateException.class, () -> channelService.createMIMChannel(
//      "streamId",
//      newIUser("1"),
//      // Use a LinkedHashMap to have predictable test results
//      Stream.of(federatedAccountsForEmp1, federatedAccountsForEmp2).collect(toMap(list -> list.get(0).getEmp(), Function.identity(), (existingValue, replacementValue) -> existingValue, LinkedHashMap::new)),
//      symphonyUsers));
//
//
//    assertEquals(UnknownDatafeedUserException.class, exception.getCause().getClass());
//
//    InOrder orderVerifier = inOrder(datafeedSessionPool, empClient, symphonyMessageSender);
//
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("101");
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("102");
//    orderVerifier.verify(empClient, once()).createChannel("emp1", "streamId", federatedAccountsForEmp1, "1", symphonyUsers);
//    orderVerifier.verify(symphonyMessageSender, once()).sendInfoMessage(userSession101, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
//    orderVerifier.verify(symphonyMessageSender, once()).sendInfoMessage(userSession102, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
//
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("201");
//
//    // Because of the problem with the userSession201, we have no other interactions
//    // emp2 is not called but emp1 has been called
//    orderVerifier.verify(datafeedSessionPool, never()).refreshSession("202");
//    orderVerifier.verify(empClient, never()).createChannel("emp2", "streamId", federatedAccountsForEmp2, "1", symphonyUsers);
//    orderVerifier.verify(symphonyMessageSender, never()).sendInfoMessage(userSession201, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
//    orderVerifier.verify(symphonyMessageSender, never()).sendInfoMessage(userSession202, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
//
//    orderVerifier.verifyNoMoreInteractions();
//  }
//
//  @Test
//  void createMIMChannel_ProblemWithOneEMP() throws UnknownDatafeedUserException {
//
//    List<FederatedAccount> federatedAccountsForEmp1 = Arrays.asList(newFederatedAccount("emp1", "101"), newFederatedAccount("emp1", "102"));
//    List<FederatedAccount> federatedAccountsForEmp2 = Arrays.asList(newFederatedAccount("emp2", "201"), newFederatedAccount("emp2", "202"));
//    List<FederatedAccount> federatedAccountsForEmp3 = Arrays.asList(newFederatedAccount("emp3", "301"), newFederatedAccount("emp3", "302"));
//    List<IUser> symphonyUsers = Arrays.asList(newIUser("2"), newIUser("3"));
//
//    when(empClient.createChannel("emp1", "streamId", federatedAccountsForEmp1, "1", symphonyUsers)).thenReturn(Optional.of("operationId1"));
//    when(empClient.createChannel("emp2", "streamId", federatedAccountsForEmp2, "1", symphonyUsers)).thenThrow(CreateChannelFailedProblem.class);
//    when(empClient.createChannel("emp3", "streamId", federatedAccountsForEmp3, "1", symphonyUsers)).thenReturn(Optional.of("operationId3"));
//
//    DatafeedSessionPool.DatafeedSession userSession101 = new DatafeedSessionPool.DatafeedSession(userSession, "101");
//    when(datafeedSessionPool.refreshSession("101")).thenReturn(userSession101);
//    DatafeedSessionPool.DatafeedSession userSession102 = new DatafeedSessionPool.DatafeedSession(userSession, "102");
//    when(datafeedSessionPool.refreshSession("102")).thenReturn(userSession102);
//    doNothing().when(symphonyMessageSender).sendInfoMessage(any(UserSession.class), eq("streamId"), anyString());
//    DatafeedSessionPool.DatafeedSession userSession201 = new DatafeedSessionPool.DatafeedSession(userSession, "201");
//    when(datafeedSessionPool.refreshSession("201")).thenReturn(userSession201);
//    DatafeedSessionPool.DatafeedSession userSession202 = new DatafeedSessionPool.DatafeedSession(userSession, "202");
//    when(datafeedSessionPool.refreshSession("202")).thenReturn(userSession202);
//    DatafeedSessionPool.DatafeedSession userSession301 = new DatafeedSessionPool.DatafeedSession(userSession, "301");
//    when(datafeedSessionPool.refreshSession("301")).thenReturn(userSession301);
//    DatafeedSessionPool.DatafeedSession userSession302 = new DatafeedSessionPool.DatafeedSession(userSession, "302");
//    when(datafeedSessionPool.refreshSession("302")).thenReturn(userSession302);
//
//    assertThrows(CreateChannelFailedProblem.class, () -> channelService.createMIMChannel(
//      "streamId",
//      newIUser("1"),
//      // Use a LinkedHashMap to have predictable test results
//      Stream.of(federatedAccountsForEmp1, federatedAccountsForEmp2).collect(toMap(list -> list.get(0).getEmp(), Function.identity(), (existingValue, replacementValue) -> existingValue, LinkedHashMap::new)),
//      symphonyUsers));
//
//    InOrder orderVerifier = inOrder(datafeedSessionPool, empClient, symphonyMessageSender);
//
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("101");
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("102");
//    orderVerifier.verify(empClient, once()).createChannel("emp1", "streamId", federatedAccountsForEmp1, "1", symphonyUsers);
//    orderVerifier.verify(symphonyMessageSender, once()).sendInfoMessage(userSession101, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
//    orderVerifier.verify(symphonyMessageSender, once()).sendInfoMessage(userSession102, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
//
//
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("201");
//    orderVerifier.verify(datafeedSessionPool, once()).refreshSession("202");
//    orderVerifier.verify(empClient, once()).createChannel("emp2", "streamId", federatedAccountsForEmp2, "1", symphonyUsers);
//
//    // Because of the problem with the emp2 we have no other interaction
//    // emp1 has been called but emp2 and emp3 have not been called
//    orderVerifier.verify(symphonyMessageSender, never()).sendInfoMessage(userSession201, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
//    orderVerifier.verify(symphonyMessageSender, never()).sendInfoMessage(userSession202, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
//    orderVerifier.verify(datafeedSessionPool, never()).refreshSession("301");
//    orderVerifier.verify(datafeedSessionPool, never()).refreshSession("302");
//    orderVerifier.verify(empClient, never()).createChannel("emp3", "streamId", federatedAccountsForEmp3, "1", symphonyUsers);
//    orderVerifier.verify(symphonyMessageSender, never()).sendInfoMessage(userSession301, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
//    orderVerifier.verify(symphonyMessageSender, never()).sendInfoMessage(userSession302, "streamId", "Hello, I will be ready as soon as I join the whatsapp group");
//
//    orderVerifier.verifyNoMoreInteractions();
//  }

  private IUser newIUser(String symphonyId) {
    UserEntity.Builder builder = new UserEntity.Builder()
      .withId(Long.valueOf(symphonyId))
      .withFirstName(symphonyId + "_firstName")
      .withSurname(symphonyId + "_lastName")
      .withCompany("companyName");
    return new User(builder);
  }

  private FederatedAccount newFederatedAccount(String emp, String symphonyUserId) {
    return FederatedAccount.builder()
      .firstName(symphonyUserId + "firstName")
      .lastName(symphonyUserId + "lastName")
      .companyName(symphonyUserId + "companyName")
      .emp(emp)
      .symphonyUserId(symphonyUserId)
      .build();
  }
}
