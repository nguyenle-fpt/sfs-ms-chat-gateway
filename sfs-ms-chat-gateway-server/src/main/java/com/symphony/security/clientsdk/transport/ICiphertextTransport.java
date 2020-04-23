package com.symphony.security.clientsdk.transport;

/**
 * Created by sergey on 5/27/15.
 */
public interface ICiphertextTransport {

  byte getVersion();

  int getPodId();

  long getRotationId();

  byte[] getIV();

  byte[] getAuthData();

  byte[] getCiphertext();

  byte[] getTag();

  byte[] getCiphertextAndTag();

  byte[] getRawData();

  byte[] getKeyId();

  byte getEncryptionMode();

  boolean hasKeyId();

  @Override
  String toString();
}
