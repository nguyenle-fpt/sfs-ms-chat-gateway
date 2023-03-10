package com.symphony.sfs.ms.chat.service;

import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.oss.models.core.canon.facade.PodAndUserId;
import com.symphony.sfs.ms.admin.generated.model.EmpList;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.config.properties.common.PemResource;
import com.symphony.sfs.ms.starter.security.StaticSessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.symphony.user.UsersInfoService;
import com.symphony.sfs.ms.starter.testing.I18nTest;
import com.symphony.sfs.ms.starter.util.UserIdUtils;
import model.UserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomServiceTest implements I18nTest {

  private FederatedAccountRepository federatedAccountRepository;

  private PodConfiguration podConfiguration;
  private BotConfiguration botConfiguration;

  private StreamService streamService;
  private AuthenticationService authenticationService;
  private DatafeedSessionPool datafeedSessionPool;

  private EmpClient empClient;
  private AdminClient adminClient;

  private RoomService roomService;
  private UsersInfoService usersInfoService;

  private SymphonySession userSession;

  private SymphonySession botSession;

  private static final long NOW = OffsetDateTime.now().toEpochSecond();
  public static final String STREAM_ID = "streamId";
  public static final String EMP1 = "EMP1";
  public static final String EMP2 = "EMP2";
  public static final String POD_ID = "1";
  public static final String FEDERATION_POD = "2";

  @BeforeEach
  public void setUp(MessageSource messageSource) {
    empClient = mock(EmpClient.class);
    authenticationService = mock(AuthenticationService.class);
    federatedAccountRepository = mock(FederatedAccountRepository.class);

    botConfiguration = new BotConfiguration();
    botConfiguration.setUsername("username");
    botConfiguration.setEmailAddress("emailAddress");
    botConfiguration.setPrivateKey(new PemResource("-----botConfigurationPrivateKey"));
    botConfiguration.setSymphonyId("1234567890");

    podConfiguration = new PodConfiguration();
    podConfiguration.setUrl("podUrl");
    podConfiguration.setSessionAuth("sessionAuth");
    podConfiguration.setKeyAuth("keyAuth");

    streamService = mock(StreamService.class);
    usersInfoService = mock(UsersInfoService.class);


    userSession = new SymphonySession("username", "kmToken", "sessionToken");
    when(authenticationService.authenticate(anyString(), anyString(), anyString(), anyString())).thenReturn(userSession);

    adminClient = mock(AdminClient.class);
    when(adminClient.getEmpList()).thenReturn(new EmpList());

    roomService = spy(new RoomService(federatedAccountRepository, podConfiguration, botConfiguration, mock(ForwarderQueueConsumer.class), streamService, datafeedSessionPool, usersInfoService, empClient, adminClient));

    botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());
  }



  @Test
  public void onUserLeftRoom_OK() {
    createFederationAccountRepositoryAndUsersInfoServiceMock(Arrays.asList("11","12"), Arrays.asList("31", "32", "33"), Collections.singletonList("41"));
   // Different ordering due to ordering of operations
    // In this case the new RoomMembers have been removed in Admin ms before the Get Room Members request was sent
    List<IUser> mockedIUsers = generateIUsersMock(Collections.singletonList("12"), Collections.singletonList("33"));
    roomService.onUserLeftRoom(STREAM_ID, newIUser("1"), mockedIUsers);

    verify(adminClient).notifyLeaveRoom(STREAM_ID, "1", Arrays.asList(symphonyId("12", POD_ID), symphonyId("33", FEDERATION_POD)));
  }

  private static IUser newIUser(String symphonyUserId) {
    PodAndUserId id = PodAndUserId.newBuilder().build(Long.valueOf(symphonyUserId));
    IUser mockIUser = mock(IUser.class);
    when(mockIUser.getId()).thenReturn(id);
    when(mockIUser.getCompany()).thenReturn("symphony");
    return mockIUser;
  }

  private void createFederationAccountRepositoryAndUsersInfoServiceMock(List<String> advisorsSuffix, List<String> emp1UsersSuffix, List<String> emp2UsersSuffix){
    for(String suffix : advisorsSuffix){
      when(federatedAccountRepository.findBySymphonyId(symphonyId(suffix, POD_ID))).thenReturn(Optional.empty());
      UserInfo userInfo = new UserInfo();
      userInfo.setFirstName("firstName" + suffix);
      userInfo.setLastName("lastName" + suffix);
      when(usersInfoService.getUserFromId(anyString(), any(StaticSessionSupplier.class), eq(symphonyId(suffix, POD_ID)))).thenReturn(Optional.of(userInfo));
    }
    for(String suffix : emp1UsersSuffix){
      when(federatedAccountRepository.findBySymphonyId(symphonyId(suffix, FEDERATION_POD))).thenReturn(Optional.of(FederatedAccount.builder().firstName("firstName" + suffix).lastName("lastName" + suffix).emp(EMP1).build()));
    }
    for(String suffix : emp2UsersSuffix){
      when(federatedAccountRepository.findBySymphonyId(symphonyId(suffix, FEDERATION_POD))).thenReturn(Optional.of(FederatedAccount.builder().firstName("firstName" + suffix).lastName("lastName" + suffix).emp(EMP2).build()));
    }
  }

  private List<IUser> generateIUsersMock(List<String> advisorsSuffix, List<String> empUsersSuffix){
    List<IUser> IUsers = new ArrayList<>();
    for (String suffix : advisorsSuffix) {
      IUser iUser = mock(IUser.class);
      PodAndUserId podAndUserId = mock(PodAndUserId.class);
      when(iUser.getId()).thenReturn(podAndUserId);
      when(podAndUserId.toString()).thenReturn(symphonyId(suffix, POD_ID));
      IUsers.add(iUser);
    }
    for (String suffix : empUsersSuffix) {
      IUser iUser = mock(IUser.class);
      PodAndUserId podAndUserId = mock(PodAndUserId.class);
      when(iUser.getId()).thenReturn(podAndUserId);
      when(podAndUserId.toString()).thenReturn(symphonyId(suffix, FEDERATION_POD));
      IUsers.add(iUser);
    }
    return IUsers;
  }

  private static String symphonyId(String suffix, String podId) {
    return UserIdUtils.buildUserId("" + suffix, podId);
  }
}
