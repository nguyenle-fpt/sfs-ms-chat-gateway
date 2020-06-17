package com.symphony.sfs.ms.chat.datafeed;

import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.service.FederatedAccountSessionService;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.UserSession;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import model.UserInfo;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class DatafeedSessionPool {

  private final AuthenticationService authenticationService;
  private final PodConfiguration podConfiguration;
  private final ChatConfiguration chatConfiguration;
  private final FederatedAccountSessionService federatedAccountSessionService;

  private Map<String, DatafeedSession> sessions = new ConcurrentHashMap<>();

  public DatafeedSession listenDatafeed(String symphonyId) throws UnknownDatafeedUserException {
    FederatedAccount account = federatedAccountSessionService.findBySymphonyIdOrFail(symphonyId);
    return listenDatafeed(account);
  }

  public DatafeedSession listenDatafeed(FederatedAccount account) {
    UserSession session = authenticationService.authenticate(
      podConfiguration.getSessionAuth(),
      podConfiguration.getKeyAuth(),
      account.getSymphonyUsername(),
      chatConfiguration.getSharedPrivateKey().getData());
    federatedAccountSessionService.updateSession(account, session);

    DatafeedSession datafeedSession = new DatafeedSession(session, account.getSymphonyUserId());
    sessions.put(account.getSymphonyUserId(), datafeedSession);
    return datafeedSession;
  }

  public DatafeedSession getSession(String symphonyId) {
    DatafeedSession session = sessions.get(symphonyId);
    if (session == null) {
      federatedAccountSessionService.findBySymphonyId(symphonyId)
        .map(account -> new DatafeedSession(new UserSession(account.getSymphonyUsername(), null, account.getKmToken(), account.getSessionToken()), symphonyId))
        .ifPresent(s -> sessions.put(symphonyId, s));

      session = sessions.get(symphonyId);
    }
    return session;
  }

  public boolean sessionExists(String symphonyId) {
    return getSession(symphonyId) != null;
  }

  public boolean sessionNotExists(String symphonyId) {
    return !sessionExists(symphonyId);
  }

  public DatafeedSession refreshSession(String symphonyId) throws UnknownDatafeedUserException {
    DatafeedSession session = getSession(symphonyId);
    if (session == null) {
      return listenDatafeed(symphonyId);
    }

    // ask for user info, to check if the session is active
    Optional<UserInfo> info = authenticationService.getUserInfo(podConfiguration.getUrl(), session, true);
    if (info.isEmpty()) {
      return listenDatafeed(symphonyId);
    }

    return session;
  }


  @Getter
  @EqualsAndHashCode(callSuper = true)
  public static class DatafeedSession extends UserSession {
    private String userId;

    public DatafeedSession(UserSession session, String userId) {
      super(session.getUsername(), session.getJwt(), session.getKmToken(), session.getSessionToken());
      this.userId = userId;
    }

    public Long getUserIdAsLong() {
      return Long.valueOf(userId);
    }
  }
}
