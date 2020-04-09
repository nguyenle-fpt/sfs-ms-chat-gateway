package com.symphony.sfs.ms.chat.datafeed;

import com.symphony.oss.models.chat.canon.facade.ISocialMessage;
import com.symphony.security.clientsdk.transport.CiphertextFactory;
import com.symphony.security.clientsdk.transport.ICiphertextTransport;
import com.symphony.security.exceptions.CiphertextTransportIsEmptyException;
import com.symphony.security.exceptions.CiphertextTransportVersionException;
import com.symphony.security.exceptions.InvalidDataException;
import com.symphony.security.exceptions.SymphonyEncryptionException;
import com.symphony.security.exceptions.SymphonyInputException;
import com.symphony.security.helper.ClientCryptoHandler;
import com.symphony.security.helper.IClientCryptoHandler;
import com.symphony.sfs.ms.chat.exception.ContentKeyRetrievalException;
import com.symphony.sfs.ms.chat.exception.DecryptionException;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MessageDecryptor {

  private IClientCryptoHandler cryptoHandler;
  private ContentKeyManager contentKeyManager;

  public MessageDecryptor(ContentKeyManager contentKeyManager) {
    this.contentKeyManager = contentKeyManager;
    this.cryptoHandler = new ClientCryptoHandler();
  }

  public String decrypt(ISocialMessage socialMessage, String userId) throws UnknownDatafeedUserException, ContentKeyRetrievalException, DecryptionException {
    try {
      // Get message ciphertext transport to extract rotation Id of key that was used to cipher the text.
      ICiphertextTransport msgCipherTransport = CiphertextFactory.getTransport(socialMessage.getText());

      byte[] contentKey = contentKeyManager.getContentKey(socialMessage.getThreadId(), userId, msgCipherTransport.getRotationId());

      return cryptoHandler.decryptMsg(contentKey, socialMessage.getText());
    } catch (SymphonyInputException | CiphertextTransportIsEmptyException | CiphertextTransportVersionException | InvalidDataException | SymphonyEncryptionException e) {
      throw new DecryptionException(e);
    }
  }

}
