package com.symphony.security.helper;

import com.symphony.security.clientsdk.transport.CiphertextFactory;
import com.symphony.security.clientsdk.transport.CiphertextTransportEncryptionMode;
import com.symphony.security.clientsdk.transport.CiphertextTransportV2;
import com.symphony.security.clientsdk.transport.CiphertextTransportV3;
import com.symphony.security.clientsdk.transport.ICiphertextTransport;
import com.symphony.security.exceptions.CiphertextTransportIsEmptyException;
import com.symphony.security.exceptions.CiphertextTransportVersionException;
import com.symphony.security.exceptions.InvalidDataException;
import com.symphony.security.exceptions.SymphonyComputedKeyIdAndExpectedKeyIdMistmatchException;
import com.symphony.security.exceptions.SymphonyEncryptionException;
import com.symphony.security.exceptions.SymphonyInputException;
import com.symphony.security.exceptions.SymphonyPEMFormatException;
import com.symphony.security.hsm.TransportableWrappedKey;
import com.symphony.security.utils.CryptoHashUtils;
import com.gs.ti.wpt.lc.security.cryptolib.AES;
import com.gs.ti.wpt.lc.security.cryptolib.RSA;

import org.apache.commons.codec.binary.StringUtils;
import org.bouncycastle.util.encoders.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.Arrays;

public class ClientCryptoHandler implements IClientCryptoHandler {

  private static Logger LOG = LoggerFactory.getLogger(ClientCryptoHandler.class.getName());

  private byte[] decryptLegacy(byte[] key, byte[] legacyPackedCiphertext)
      throws IOException, SymphonyEncryptionException, SymphonyInputException {
    TransportableWrappedKey wrappedContentKeyOnTheWire = null;

    wrappedContentKeyOnTheWire = new TransportableWrappedKey(legacyPackedCiphertext);

    byte[] iv = wrappedContentKeyOnTheWire.getIv();
    byte[] wrappedContentKeyBytes = wrappedContentKeyOnTheWire.getWrappedKeyBytes();

    if (iv.length != 16)
      throw new SymphonyEncryptionException(
          "iv in wrappedContentKeyOnTheWire is invalid, expected 16 bytes, found " + iv.length);
    if (wrappedContentKeyBytes.length != 32)
      throw new SymphonyEncryptionException(
          "wrappedContentKey in wrappedContentKeyOnTheWire is invalid, expected 32 bytes, found "
              + wrappedContentKeyBytes.length);

    byte[] clear = AES.DecryptCBC(wrappedContentKeyBytes, key, iv);
    if (clear.length != 32) {
      throw new SymphonyEncryptionException(
          "CK is invalid, expected 32 bytes, found " + clear.length);
    }
    return clear;
  }

  private byte[] decrypt(byte[] key, ICiphertextTransport ciphertext)
      throws IOException, SymphonyEncryptionException, SymphonyInputException, InvalidDataException,
      CiphertextTransportIsEmptyException, CiphertextTransportVersionException {

    byte encryptionMode = ciphertext.getEncryptionMode();



    byte[] plaintext = null;
    try {
      if (encryptionMode == CiphertextTransportEncryptionMode.AES_GCM) {
        plaintext = AES.DecryptGCM(ciphertext.getCiphertext(), ciphertext.getAuthData(), key,
            ciphertext.getIV(), ciphertext.getTag());
      } else if (encryptionMode == CiphertextTransportEncryptionMode.AES_CBC) {
        plaintext = AES.DecryptCBC(ciphertext.getCiphertext(), key, ciphertext.getIV());
      } else if (encryptionMode == CiphertextTransportEncryptionMode.RSA_OAEP) {
        try {
          plaintext = RSA.Decrypt(new String(key, Charset.forName("UTF-8")), ciphertext.getCiphertext(),
              RSA.CL_RSA_PKCS1V20_OAEP_PADDING);
        } catch (SymphonyPEMFormatException e) {
          LOG.error("Invalid PEM format for RSA private key found on decryption attempt.", e);
          throw new InvalidDataException(e);
        }
      }
    } catch (SymphonyEncryptionException ex) {
      // If we can't decrypt the payload, if the fingerprints don't match we throw a
      // SymphonyComputedKeyIdAndExpectedKeyIdMistmatchException
      checkFingerPrints(key, ciphertext, false);
      throw ex;
    }
    checkFingerPrints(key, ciphertext, true);
    return plaintext;
  }

  private void checkFingerPrints(byte[] key, ICiphertextTransport ciphertext, boolean logOnly)
      throws SymphonyComputedKeyIdAndExpectedKeyIdMistmatchException {
    byte[] keyId = ciphertext.getKeyId();
    if (keyId != null) {
      byte[] computedKeyId = CryptoHashUtils.keyFingerprint(key);
      if (!Arrays.equals(keyId, computedKeyId)) {
        if (logOnly) {
          LOG.warn(
              "Fingerprints did not match, but message was decryptable. Indicates the client added the wrong "
                  + "fingerprint: Theirs: ["
                  + org.apache.commons.codec.binary.Base64
                  .encodeBase64String(keyId) + "] Mine: [" + org.apache.commons.codec.binary.Base64.encodeBase64String(
                  computedKeyId) + "]");
        } else {
          throw new SymphonyComputedKeyIdAndExpectedKeyIdMistmatchException(keyId, computedKeyId);
        }
      }
    }
  }

  @Override
  public String decryptMsg(byte[] key, String cipherText)
      throws SymphonyInputException, CiphertextTransportIsEmptyException,
      CiphertextTransportVersionException, InvalidDataException, SymphonyEncryptionException {
    byte[] rawCipherBytes = Base64.decode(cipherText);
    byte[] plainBytes = decryptMsg(key, rawCipherBytes);
    return StringUtils.newStringUtf8(plainBytes);
  }

  @Override
  public byte[] decryptMsg(byte[] key, byte[] rawCipherText)
      throws SymphonyEncryptionException, SymphonyInputException, InvalidDataException,
      CiphertextTransportIsEmptyException, CiphertextTransportVersionException {

    if (rawCipherText.length == 48) {
      try {
        return decryptLegacy(key, rawCipherText);
      } catch (IOException e) {
        LOG.error("Failed to decrypt legacy wrapped ciphertext", e);
        throw new SymphonyInputException(e);
      }
    }
    try {
      return decrypt(key, CiphertextFactory.getTransport(rawCipherText));
    } catch (IOException e) {
      LOG.error("Failed to decrypt ICiphertextTransport wrapped ciphertext", e);
      throw new SymphonyInputException(e);
    }
  }

  private byte[] encryptMsg(byte[] key, byte[] plaintext, KeyIdentifier keyId, int ciphertextTransportVersion, byte mode)
      throws SymphonyEncryptionException, SymphonyInputException, CiphertextTransportVersionException {

    byte[] IV = new byte[16];
    byte[] AAD = new byte[16];
    byte[] tag = new byte[16];

    // Pick some secure values.
    SecureRandom SR = new SecureRandom();
    SR.nextBytes(IV);
    SR.nextBytes(AAD);

    // Encrypt.
    byte[] cipherText = null;
    if (ciphertextTransportVersion == 2 || mode == CiphertextTransportEncryptionMode.AES_GCM) {

      cipherText = AES.EncryptGCM(plaintext, AAD, key, IV, tag);

    } else if (mode == CiphertextTransportEncryptionMode.AES_CBC) {

      if (plaintext.length % 16 != 0) {
        String err = String.format("Invalid plaintext length of %d in plaintext to encrypt CBC in unpadded format", plaintext.length);
        LOG.error(err);
        throw new SymphonyInputException(err);
      }

      cipherText = AES.EncryptCBC(plaintext, key, IV);
    } else {
      String err = "unable to encrypt with unsupported encryption mode of " + mode;
      LOG.error(err);
      throw new SymphonyEncryptionException(err);
    }

    // Pack.
    ICiphertextTransport ciphertextTransport = null;
    if (ciphertextTransportVersion == 2) {

      // This ciphertext format only supports rotationId 0.
      if (keyId.getRotationId() != 0) {
        throw new SymphonyEncryptionException(String.format("Invalid rotationId of {} for ciphertext transport version 2 detected.", keyId.getRotationId()));
      }

      ciphertextTransport = new CiphertextTransportV2(cipherText, AAD, IV, tag, 0, 0);
    } else if (ciphertextTransportVersion == 3) {

      byte[] computedKeyId = Base64.decode(CryptoHashUtils.keyFingerprint(Base64.toBase64String(key)));

      ciphertextTransport = new CiphertextTransportV3(cipherText, AAD, IV, tag, 0, keyId.getRotationId(), computedKeyId, mode);
    } else {
      throw new CiphertextTransportVersionException(String.format("Unsupported version requested {}", ciphertextTransportVersion));
    }

    // Return.
    return ciphertextTransport.getRawData();
  }

  @Override
  public byte[] encryptMsgWithRotationIdZero(byte[] key, byte[] plaintext) throws SymphonyEncryptionException, SymphonyInputException {
    try {
      KeyIdentifier genericV2CompatibleKeyId = new KeyIdentifier(new byte[25], 0l, 0l);
      return encryptMsg(key, plaintext, genericV2CompatibleKeyId, getPackagedCiphertextVersion(0), CiphertextTransportEncryptionMode.AES_GCM);
    } catch (CiphertextTransportVersionException e) {
      LOG.error("Unkonwn ciphertext transport version requested.  Did encryptMsg line change above?", e);
      throw new SymphonyEncryptionException(e);
    }
  }

  private int getPackagedCiphertextVersion(long rotationId) {
    return 3;
  }

  @Override
  public byte[] encryptMsg(byte[] key, KeyIdentifier keyId, byte[] plaintext)
      throws SymphonyEncryptionException, SymphonyInputException,
      CiphertextTransportVersionException {

    int packagedCiphertextVersion = getPackagedCiphertextVersion(keyId.getRotationId());
    return encryptMsg(key, plaintext, keyId, packagedCiphertextVersion, CiphertextTransportEncryptionMode.AES_GCM);

  }

  @Override
  public byte[] encryptMsg(byte[] key, KeyIdentifier keyId, byte[] plaintext, byte ciphertextTransportEncryptionMode)
      throws SymphonyEncryptionException, SymphonyInputException,
      CiphertextTransportVersionException {

    int packagedCiphertextVersion = getPackagedCiphertextVersion(keyId.getRotationId());
    return encryptMsg(key, plaintext, keyId, packagedCiphertextVersion, ciphertextTransportEncryptionMode);

  }

}
