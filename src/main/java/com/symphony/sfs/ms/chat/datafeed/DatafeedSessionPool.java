package com.symphony.sfs.ms.chat.datafeed;

import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.UserSession;
import lombok.Getter;
import model.UserInfo;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class DatafeedSessionPool {

  private AuthenticationService authenticationService;
  private PodConfiguration podConfiguration;
  private ChatConfiguration chatConfiguration;

  private Map<String, DatafeedSession> sessions = new HashMap<>();

  public DatafeedSessionPool(AuthenticationService authenticationService, PodConfiguration podConfiguration, ChatConfiguration chatConfiguration) {
    this.authenticationService = authenticationService;
    this.podConfiguration = podConfiguration;
    this.chatConfiguration = chatConfiguration;
  }

  public DatafeedSession listenDatafeed(String username) {
    UserSession session = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), username, chatConfiguration.getSharedPrivateKey().getData());
    UserInfo info = authenticationService.getUserInfo(podConfiguration.getUrl(), session, true).get(); // TODO proper exception
    return sessions.put(username, new DatafeedSession(session, info.getId()));
  }

  public DatafeedSession listenDatafeed(String username, Long userId) {
    UserSession session = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), username, chatConfiguration.getSharedPrivateKey().getData());
    return sessions.put(username, new DatafeedSession(session, userId));
  }

  public DatafeedSession getSession(String username) {
    return sessions.get(username);
  }

  public DatafeedSession refreshSession(String username, Long userId) {
    DatafeedSession session = getSession(username);
    if (session == null) {
      return listenDatafeed(username, userId);
    }

    // ask for user info, to check if the session is active
    Optional<UserInfo> info = authenticationService.getUserInfo(podConfiguration.getUrl(), session, true);
    if (info.isEmpty()) {
      return listenDatafeed(username, userId);
    }

    return session;
  }


  @Getter
  public static class DatafeedSession extends UserSession {
    private Long userId;

    public DatafeedSession(UserSession session, Long userId) {
      super(session.getUsername(), session.getJwt(), session.getKmToken(), session.getSessionToken());
      this.userId = userId;
    }
  }
}
