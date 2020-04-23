package com.symphony.security.helper;

import com.symphony.security.clientsdk.transport.CiphertextFactory;
import com.symphony.security.clientsdk.transport.CiphertextTransportEncryptionMode;
import com.symphony.security.clientsdk.transport.ICiphertextTransport;
import com.symphony.security.exceptions.CiphertextTransportIsEmptyException;
import com.symphony.security.exceptions.InvalidDataException;
import com.symphony.security.exceptions.SymphonyEncryptionException;
import com.symphony.security.exceptions.SymphonyInputException;
import com.symphony.security.exceptions.CiphertextTransportVersionException;

import org.apache.commons.codec.binary.Base64;

import java.nio.charset.StandardCharsets;

/**
 * If you want to save your key, you can use this interface to encrypt and decrypt instead of
 * IClientCryptoHandler.
 */
public class CDecryptionHelper implements IDecryptionHelper {

  private byte[] secretKey;
  private IClientCryptoHandler clientCryptoHandler;

  public CDecryptionHelper(String roomKey) {
    this.secretKey = Base64.decodeBase64(roomKey);
    this.clientCryptoHandler = new ClientCryptoHandler();
  }

  public CDecryptionHelper(byte[] roomKey) {
    this.secretKey = roomKey;
    this.clientCryptoHandler = new ClientCryptoHandler();
  }

  @Override
  public String decrypt(String message)
      throws SymphonyEncryptionException, InvalidDataException, CiphertextTransportVersionException,
      CiphertextTransportIsEmptyException, SymphonyInputException {
      ICiphertextTransport symphonyCipherText = CiphertextFactory.getTransport(message);
      if (symphonyCipherText.getCiphertext().length == 0)
        return "";

    return clientCryptoHandler.decryptMsg(secretKey, message);

  }

  @Override
  public byte[] decrypt(byte[] message)
      throws SymphonyEncryptionException, SymphonyInputException, InvalidDataException,
      CiphertextTransportIsEmptyException, CiphertextTransportVersionException {
      return clientCryptoHandler.decryptMsg(secretKey, message);
  }

  @Override
  public String encrypt(String message, int version, int podId, long rotationId) throws SymphonyEncryptionException {
    byte[] encryptedData = encrypt(message.getBytes(StandardCharsets.UTF_8), version, podId, rotationId);
    return Base64.encodeBase64String(encryptedData);
  }

  @Override
  public byte[] encrypt(byte[] message, int version, int podId, long rotationId) throws SymphonyEncryptionException {
    return encrypt(message, version, podId, rotationId, CiphertextTransportEncryptionMode.AES_GCM);
  }

  @Override
  public byte[] encrypt(byte[] message, int version, int podId, long rotationId, byte mode) throws SymphonyEncryptionException {

    KeyIdentifier keyId = new KeyIdentifier(null, null, rotationId);

    try {
      return clientCryptoHandler.encryptMsg(secretKey, keyId, message, mode);
    } catch (SymphonyInputException | CiphertextTransportVersionException e) {
      throw new SymphonyEncryptionException(e);
    }
  }

}
