package com.symphony.security.helper;

import com.symphony.security.exceptions.CiphertextTransportIsEmptyException;
import com.symphony.security.exceptions.CiphertextTransportVersionException;
import com.symphony.security.exceptions.InvalidDataException;
import com.symphony.security.exceptions.SymphonyEncryptionException;
import com.symphony.security.exceptions.SymphonyInputException;

/**
 * If you want to save your key, you can use this interface to encrypt and decrypt instead of
 * IClientCryptoHandler.
 */
public interface IDecryptionHelper {

  String decrypt(String message)
      throws SymphonyEncryptionException, InvalidDataException, CiphertextTransportVersionException,
      CiphertextTransportIsEmptyException, SymphonyInputException;

  byte[] decrypt(byte[] message)
      throws SymphonyEncryptionException, SymphonyInputException, InvalidDataException,
      CiphertextTransportIsEmptyException, CiphertextTransportVersionException;

  String encrypt(String message, int version, int podId, long rotationId) throws SymphonyEncryptionException;

  byte[] encrypt(byte[] message, int version, int podId, long rotationId) throws SymphonyEncryptionException;



  /**
   *
   * FOR TESTING PURPOSES ONLY.
   *
   * @param message
   * @param version
   * @param podId
   * @param rotationId
   * @param encryptionMode
   * @return
   * @throws SymphonyEncryptionException
   */
  byte[] encrypt(byte[] message, int version, int podId, long rotationId, byte encryptionMode) throws SymphonyEncryptionException;
}
