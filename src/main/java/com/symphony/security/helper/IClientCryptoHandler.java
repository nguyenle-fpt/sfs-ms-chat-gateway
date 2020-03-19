package com.symphony.security.helper;

import com.symphony.security.exceptions.CiphertextTransportIsEmptyException;
import com.symphony.security.exceptions.InvalidDataException;
import com.symphony.security.exceptions.SymphonyEncryptionException;
import com.symphony.security.exceptions.SymphonyInputException;
import com.symphony.security.exceptions.CiphertextTransportVersionException;

public interface IClientCryptoHandler {

  /**
   * @param cipherText Entire on-the-wire formatted message body
   * @return decrypted message
   */
  String decryptMsg(byte[] key, String cipherText) throws SymphonyEncryptionException,
      SymphonyInputException, CiphertextTransportIsEmptyException,
      CiphertextTransportVersionException, InvalidDataException;

  byte[] decryptMsg(byte[] key, byte[] cipherBytes) throws SymphonyEncryptionException,
      SymphonyInputException, InvalidDataException, CiphertextTransportIsEmptyException,
      CiphertextTransportVersionException;

  /**
   * DEPRECATED. Assumes that key being passed in has rotationId = 0.
   *
   * @param plaintext plain message content
   * @return Transport-ready on-the-wire formatted encrypted message body
   */
  @Deprecated
  byte[] encryptMsgWithRotationIdZero(byte[] key, byte[] plaintext) throws SymphonyEncryptionException, SymphonyInputException;


  /**
   * Encrypts plaintext with key, which is a key that can be identified with keyId.
   *
   * @param plaintext plain message content
   * @return Transport-ready on-the-wire formatted encrypted message body
   */
  byte[] encryptMsg(byte[] key, KeyIdentifier keyId, byte[] plaintext)
      throws SymphonyEncryptionException, SymphonyInputException,
      CiphertextTransportVersionException;

  byte[] encryptMsg(byte[] key, KeyIdentifier keyId, byte[] plaintext, byte ciphertextTransportEncryptionMode)
      throws SymphonyEncryptionException, SymphonyInputException,
      CiphertextTransportVersionException;
}
