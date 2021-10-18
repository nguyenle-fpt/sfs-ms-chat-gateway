package com.symphony.sfs.ms.chat.datafeed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.symphony.oss.models.chat.canon.facade.ISocialMessage;
import com.symphony.oss.models.core.canon.facade.ThreadId;
import com.symphony.security.clientsdk.transport.CiphertextFactory;
import com.symphony.security.clientsdk.transport.ICiphertextTransport;
import com.symphony.security.exceptions.*;
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

  public void decrypt(SBEEventMessage message, String userId) throws DecryptionException {
    try {
      message.setId(message.getMessageId());
      message.setStreamId(message.getThreadId());
      // Get message ciphertext transport to extract rotation Id of key that was used to cipher the text.
      ICiphertextTransport msgCipherTransport = CiphertextFactory.getTransport(message.getText());

      byte[] contentKey = contentKeyManager.getContentKey(ThreadId.newBuilder().build(message.getThreadId()), userId, msgCipherTransport.getRotationId());
      if (message.getPresentationML() != null) {
        String decryptedPresentationML = cryptoHandler.decryptMsg(contentKey, message.getPresentationML());
        message.setPresentationML(decryptedPresentationML);
      }

      if (message.getText() != null) {
        String decryptedText = cryptoHandler.decryptMsg(contentKey, message.getText());
        message.setText(decryptedText);
      }

      if (message.getEntityJSON() != null) {
        String decryptedData = cryptoHandler.decryptMsg(contentKey, message.getEntityJSON());
        message.setEntityJSON(decryptedData);
      }

      if (message.getCustomEntities() != null) {
        String decryptedData = cryptoHandler.decryptMsg(contentKey, message.getCustomEntities());
        message.setParsedCustomEntities(CustomEntity.fromJSONString(decryptedData, objectMapper));
        message.setCustomEntities(decryptedData);
      }

      if (message.getEncryptedMedia() != null) {
        String decryptedData = cryptoHandler.decryptMsg(contentKey, message.getEncryptedMedia());
        message.setEncryptedMedia(decryptedData);
      }

      if (message.getEncryptedEntities() != null) {
        String decryptedData = cryptoHandler.decryptMsg(contentKey, message.getEncryptedEntities());
        message.setEncryptedEntities(decryptedData);
      }

      if (message.getJsonMedia() != null) {
        String decryptedData = cryptoHandler.decryptMsg(contentKey, message.getJsonMedia());
        message.setJsonMedia(decryptedData);
      }
    } catch (UnknownDatafeedUserException | ContentKeyRetrievalException | CiphertextTransportIsEmptyException | CiphertextTransportVersionException | InvalidDataException | SymphonyEncryptionException | SymphonyInputException | JsonProcessingException e) {
      throw new DecryptionException(e);
    }
  }
}
