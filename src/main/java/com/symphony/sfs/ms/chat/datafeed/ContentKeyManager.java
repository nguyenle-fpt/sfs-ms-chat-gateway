package com.symphony.sfs.ms.chat.datafeed;

import com.symphony.oss.models.core.canon.facade.ThreadId;
import com.symphony.security.cache.IPersister;
import com.symphony.security.cache.InMemoryPersister;
import com.symphony.security.clientsdk.IClientKeyRetrieverHandler;
import com.symphony.security.clientsdk.client.AuthProvider;
import com.symphony.security.clientsdk.client.ClientIdentifierFilter;
import com.symphony.security.clientsdk.client.SymphonyClient;
import com.symphony.security.clientsdk.client.SymphonyClientConfig;
import com.symphony.security.clientsdk.client.impl.SymphonyClientFactory;
import com.symphony.security.clientsdk.core.ClientKeyRetrieverException;
import com.symphony.security.clientsdk.core.ClientKeyRetrieverHandler;
import com.symphony.security.exceptions.SymphonyEncryptionException;
import com.symphony.security.exceptions.SymphonyInputException;
import com.symphony.security.exceptions.SymphonyNativeException;
import com.symphony.security.exceptions.SymphonyPEMFormatException;
import com.symphony.security.exceptions.SymphonySignatureException;
import com.symphony.security.helper.KeyIdentifier;
import com.symphony.sfs.ms.chat.config.CachingConfiguration;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool.DatafeedSession;
import com.symphony.sfs.ms.chat.exception.ContentKeyRetrievalException;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.symphony.auth.UserSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Slf4j
@Service
public class ContentKeyManager {
  private DatafeedSessionPool datafeedSessionPool;
  private PodConfiguration podConfiguration;

  private IPersister authSecretPersister;

  public ContentKeyManager(PodConfiguration podConfiguration, DatafeedSessionPool datafeedSessionPool) {
    this.podConfiguration = podConfiguration;
    this.datafeedSessionPool = datafeedSessionPool;

    // avoid reusing SecureLocalPersistence, as it assumes file presence on file system, which is not compatible with micro services operational model.
    this.authSecretPersister = new InMemoryPersister();
  }

  @Cacheable(CachingConfiguration.CONTENT_KEY_CACHE)
  public byte[] getContentKey(ThreadId threadId, String username, Long userId, Long rotationId) throws UnknownDatafeedUserException, ContentKeyRetrievalException {
    // if there is no session, it means that this user is not managed by our gateway
    if (datafeedSessionPool.getSession(username) == null) {
      throw new UnknownDatafeedUserException("Unknown datafeed user: " + username);
    }

    // make sure we have an up-to-date session
    DatafeedSession session = datafeedSessionPool.refreshSession(username, userId);

    SymphonyClient client = getSymphonyClient(session.getUsername());
    AuthProvider auth = getAuthProvider(session);

    try {
      IClientKeyRetrieverHandler clientKeyRetriever = new ClientKeyRetrieverHandler(client, authSecretPersister);
      clientKeyRetriever.init(auth, authSecretPersister, false);

      KeyIdentifier keyId = new KeyIdentifier(threadId.asImmutableByteArray().toByteArray(), userId, rotationId);
      return clientKeyRetriever.getKey(auth, keyId);
    } catch (ClientKeyRetrieverException | SymphonyInputException | UnsupportedEncodingException | SymphonySignatureException | SymphonyPEMFormatException | SymphonyNativeException | SymphonyEncryptionException e) {
      throw new ContentKeyRetrievalException(threadId, username, rotationId, e);
    }
  }

  private SymphonyClient getSymphonyClient(String username) {
    SymphonyClientConfig clientConfig = new SymphonyClientConfig();
    clientConfig.setAcountName(username);
    clientConfig.setKeymanagerUrl(podConfiguration.getKeyAuth() + "/relay/");
    clientConfig.setLoginUrl(podConfiguration.getSessionAuth() + "/login/");
    clientConfig.setSymphonyUrl(podConfiguration.getUrl() + "/");
    return new SymphonyClientFactory().getClient(new ClientIdentifierFilter[0], clientConfig);
  }

  private AuthProvider getAuthProvider(UserSession session) {
    AuthProvider auth = new AuthProvider(authSecretPersister);

    // Challenge and response paths are required for SHARED_KEY authentication
    auth.setSymphonyChallengePath(AuthProvider.STD_SYMPHONY_KEY_CHALLENGE);
    auth.setSymphonyResponsePath(AuthProvider.STD_SYMPHONY_KEY_RESPONSE);
    auth.setKeyManagerChallengePath(AuthProvider.STD_KEY_MANAGER_KEY_CHALLENGE);
    auth.setKeyManagerResponsePath(AuthProvider.STD_KEY_MANAGER_KEY_RESPONSE);
    // Make sure to authenticate with Key Manager as well, as it will be hit to retrieve User Key
    auth.setAuthKeyManager(true);

    // Make shared key available to authentication layer. Shared Key should rather come from a secrets management component, instead of application properties.
    auth.getSymphonyAuth().setSession(session.getSessionToken());
    auth.getKeyManagerAuth().setSession(session.getKmToken());

    return auth;
  }
}
