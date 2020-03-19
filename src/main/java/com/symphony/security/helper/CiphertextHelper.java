package com.symphony.security.helper;

import com.symphony.security.clientsdk.transport.CiphertextFactory;
import com.symphony.security.clientsdk.transport.ICiphertextTransport;
import com.symphony.security.exceptions.CiphertextTransportIsEmptyException;
import com.symphony.security.exceptions.CiphertextTransportVersionException;
import com.symphony.security.exceptions.InvalidDataException;
import com.symphony.security.exceptions.SymphonyEncryptionException;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by miroslav.gavrilov on 7/6/18.
 * Everything related to PackagedCipherText
 */
public class CiphertextHelper {
  private static Logger LOGGER = LoggerFactory.getLogger(CiphertextFactory.class);

  public CiphertextHelper() {
    //default constructor
  }

  public long extractWrappingKeyRotationIdFromEncryptedText(String encryptedText)
      throws SymphonyEncryptionException {
    if (encryptedText == null || encryptedText.isEmpty())
      throw new SymphonyEncryptionException("Provided encrypted text is null or empty.");

    long rotationId = 0;
    byte[] textBytes = Base64.decodeBase64(encryptedText);
    //if length is 48 then this is implied to be old TransportableWrappedKey
    if (textBytes.length == 48) {
      rotationId = 0;
    } else {
      //otherwise it may be one of the PackagedCiphertext structures
      try {
        ICiphertextTransport textData = CiphertextFactory.getTransport(textBytes);
        rotationId = textData.getRotationId();
      } catch (InvalidDataException | CiphertextTransportVersionException | CiphertextTransportIsEmptyException e) {
        LOGGER.error("Unable to convert encrypted text data into PackagedCiphertext", e);
        throw new SymphonyEncryptionException("Unable to convert encrypted text data into PackagedCiphertext", e);
      } catch(IllegalStateException ise) {
        //this exception is thrown from CiphertextTransportV1 which does not support rotationId
        rotationId = 0;
      }

    }


    return rotationId;
  }

}
