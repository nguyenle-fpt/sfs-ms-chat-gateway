package com.symphony.security.clientsdk.transport;

import com.symphony.security.exceptions.CiphertextTransportIsEmptyException;
import com.symphony.security.exceptions.CiphertextTransportVersionException;
import com.symphony.security.exceptions.InvalidDataException;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by sergey on 5/27/15.
 */
public class CiphertextFactory {

  private static Logger LOG = LoggerFactory.getLogger(CiphertextFactory.class);


  public static ICiphertextTransport getTransport(String transportableBase64) throws
      InvalidDataException, CiphertextTransportIsEmptyException, CiphertextTransportVersionException {

    if (!Base64.isBase64(transportableBase64)) {
      throw new InvalidDataException("Specified string is not Base64: " + transportableBase64);
    }

    if (containsWhiteSpace(transportableBase64)) {
      throw new InvalidDataException("Specified string is not Base64, because it contains whitespace: " + transportableBase64);
    }

    return getTransport(Base64.decodeBase64(transportableBase64));
  }

  private static boolean containsWhiteSpace(String transportableBase64) {
    for(char c:transportableBase64.toCharArray()) {
      if(Character.isWhitespace(c)) return true;
    }
    return false;
  }

  public static ICiphertextTransport getTransport(byte[] transportableBytes)
      throws InvalidDataException, CiphertextTransportVersionException,
      CiphertextTransportIsEmptyException {
    if (transportableBytes == null)
      throw new InvalidDataException("encrypted data is null");

    if (transportableBytes.length == 0) {
      throw new CiphertextTransportIsEmptyException("CiphertextTransport length is zero.");
    }

    byte version = transportableBytes[0];
    switch (version) {
      case 1:
        return new CiphertextTransportV1(transportableBytes);
      case 2:
        return new CiphertextTransportV2(transportableBytes);
      case 3:
        return new CiphertextTransportV3(transportableBytes);
      default:
        StringBuilder err = new StringBuilder("Version ");
        err.append(version);
        err.append(" of CiphertextTransport is not supported. transportableBytes = ");
        err.append(Base64.encodeBase64String(transportableBytes));
        LOG.error(err.toString());
        throw new CiphertextTransportVersionException(err.toString());
    }
  }

}
