package com.symphony.security.clientsdk.transport;

/**
 * Created by jon on 6/21/16.
 */
public class CiphertextTransportEncryptionMode {

  // See https://perzoinc.atlassian.net/wiki/display/CRYPTO/PackagedCiphertext%27s+Mode
  public static final byte AES_GCM = 0;
  public static final byte AES_CBC = 1;
  public static final byte RSA_OAEP = 2;
  public static final byte RSA_PSS = 3;


}
