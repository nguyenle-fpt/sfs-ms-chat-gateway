package com.symphony.sfs.ms.chat.datafeed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.symphony.oss.models.chat.canon.facade.ISocialMessage;
import com.symphony.oss.models.core.canon.facade.ThreadId;
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
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MessageDecryptor {

  private IClientCryptoHandler cryptoHandler;
  private ContentKeyManager contentKeyManager;
  private ObjectMapper objectMapper;

  public MessageDecryptor(ContentKeyManager contentKeyManager, ObjectMapper objectMapper) {
    this.contentKeyManager = contentKeyManager;
    this.cryptoHandler = new ClientCryptoHandler();
    this.objectMapper = objectMapper;
  }

  public void decrypt(ISocialMessage socialMessage, String userId, GatewaySocialMessage gatewaySocialMessage) throws UnknownDatafeedUserException, ContentKeyRetrievalException, DecryptionException {
    try {
      // Get message ciphertext transport to extract rotation Id of key that was used to cipher the text.
      ICiphertextTransport msgCipherTransport = CiphertextFactory.getTransport(socialMessage.getText());

      byte[] contentKey = contentKeyManager.getContentKey(socialMessage.getThreadId(), userId, msgCipherTransport.getRotationId());
      if (socialMessage.getText() != null) {
        gatewaySocialMessage.setTextContent(cryptoHandler.decryptMsg(contentKey, socialMessage.getText()));

      }
      if (socialMessage.getPresentationML() != null) {
        gatewaySocialMessage.setPresentationMLContent(cryptoHandler.decryptMsg(contentKey, socialMessage.getPresentationML()));
      }
      if(socialMessage.getCustomEntities() != null) {
        String customEntitiesJson = new String(cryptoHandler.decryptMsg(contentKey, socialMessage.getCustomEntities().toByteArray()));
        gatewaySocialMessage.setCustomEntities(CustomEntity.fromJSONString(customEntitiesJson, objectMapper));

      }
    } catch (SymphonyInputException | CiphertextTransportIsEmptyException | CiphertextTransportVersionException | InvalidDataException | SymphonyEncryptionException e) {
      throw new DecryptionException(e);
    } catch (JsonProcessingException e) {
      LOG.error("Error parsing custom entities in social message | {} ", e.getMessage());
    }
  }

  public void decrypt(SBEEventMessage socialMessage, String userId) throws UnknownDatafeedUserException, ContentKeyRetrievalException, DecryptionException {
    try {
      // Get message ciphertext transport to extract rotation Id of key that was used to cipher the text.
      ICiphertextTransport msgCipherTransport = CiphertextFactory.getTransport(socialMessage.getText());

      byte[] contentKey = contentKeyManager.getContentKey(ThreadId.newBuilder().build(socialMessage.getThreadId()), userId, msgCipherTransport.getRotationId());
      if (socialMessage.getText() != null) {
        socialMessage.setText(cryptoHandler.decryptMsg(contentKey, socialMessage.getText()));

      }
      if (socialMessage.getPresentationML() != null) {
        socialMessage.setPresentationML(cryptoHandler.decryptMsg(contentKey, socialMessage.getPresentationML()));
      }


      if(socialMessage.getCustomEntities() != null) {
        String customEntitiesJson = cryptoHandler.decryptMsg(contentKey, socialMessage.getCustomEntities());
        socialMessage.setParsedCustomEntities(CustomEntity.fromJSONString(customEntitiesJson, objectMapper));

      }
    } catch (SymphonyInputException | CiphertextTransportIsEmptyException | CiphertextTransportVersionException | InvalidDataException | SymphonyEncryptionException | JsonProcessingException e) {
      throw new DecryptionException(e);
    }

  }

}
