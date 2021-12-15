package com.symphony.sfs.ms.chat.datafeed;

import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.service.FederatedAccountSessionService;
import com.symphony.sfs.ms.starter.security.ISessionManager;
import com.symphony.sfs.ms.starter.security.SessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonyAuthFactory;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DatafeedSessionPool {

  private final ChatConfiguration chatConfiguration;
  private final FederatedAccountSessionService federatedAccountSessionService;
  private final SymphonyAuthFactory symphonyAuthFactory;
  private final ISessionManager sessionManager;



  @NewSpan
  public SymphonySession openSession(String symphonyId) throws UnknownDatafeedUserException {
    FederatedAccount account = federatedAccountSessionService.findBySymphonyIdOrFail(symphonyId);
    return openSession(account);
  }

  @NewSpan
  public SymphonySession openSession(FederatedAccount account) {
    return sessionManager.openSession(getSessionSupplier(account));

  }
  public SymphonySession getSession(String symphonyId) throws UnknownDatafeedUserException {
    return sessionManager.openSession(getSessionSupplierOrFail(symphonyId));
  }

  @NewSpan
  public SessionSupplier<SymphonySession> getSessionSupplierOrFail(String symphonyId) throws UnknownDatafeedUserException {
    FederatedAccount account = federatedAccountSessionService.findBySymphonyIdOrFail(symphonyId);
    return getSessionSupplier(account);
  }

  public SessionSupplier<SymphonySession> getSessionSupplier(String symphonyId) {
    Optional<FederatedAccount> account = federatedAccountSessionService.findBySymphonyId(symphonyId);
    if (account.isEmpty()) {
      return null;
    }
    return getSessionSupplier(account.get());
  }

  public SessionSupplier<SymphonySession> getSessionSupplier(FederatedAccount account) {
    return symphonyAuthFactory.getRsaAuth(account.getSymphonyUsername(), chatConfiguration.getSharedPrivateKey().getData());
  }

  public SessionSupplier<SymphonySession> getBotSessionSupplier() {
    return symphonyAuthFactory.getBotAuth();
  }

  @NewSpan
  public boolean sessionExists(String symphonyId) {
    return federatedAccountSessionService.findBySymphonyId(symphonyId).map(f -> sessionManager.sessionExists(f.getSymphonyUsername())).orElse(false);
  }

  @NewSpan
  public boolean sessionNotExists(String symphonyId) {
    return !sessionExists(symphonyId);
  }


  public void removeSessionInMemory(String symphonyId) {
    sessionManager.deleteSession(symphonyId);
  }

}
