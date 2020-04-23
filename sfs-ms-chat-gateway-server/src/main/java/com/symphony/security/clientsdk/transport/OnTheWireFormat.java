package com.symphony.security.clientsdk.transport;

import com.symphony.security.exceptions.OnTheWireFormatException;

import java.util.Arrays;

public class OnTheWireFormat {

  byte[] message;

  public OnTheWireFormat(byte[] raw) throws OnTheWireFormatException {
    if (raw.length < 50)
      throw new OnTheWireFormatException("Raw bytes too small to be OnTheWireFormat");
    message = raw;
  }

  public OnTheWireFormat(byte[] cipherText, byte version, byte password, byte[] salt, byte[] IV, byte[] AAD, byte[] tag) throws OnTheWireFormatException {

    if (IV.length != 16)
      throw new OnTheWireFormatException("IV must be of length 16, not " + IV.length);
    if (AAD.length != 16)
      throw new OnTheWireFormatException("AAD must be of length 16, not " + AAD.length);
    if (tag.length != 16)
      throw new OnTheWireFormatException("tag must be of length 16, not " + AAD.length);

    boolean hasSalt = (password & 0x01) == 1;
    int postSaltOffset = hasSalt ? 16 : 0;

    if (hasSalt)
      message = new byte[66 + cipherText.length];
    else
      message = new byte[50 + cipherText.length];

    message[0] = version;
    message[1] = password;

    for (int i = 0; hasSalt && i < 16; ++i) {
      message[2 + i] = salt[i];
    }

    for (int i = 0; i < 16; ++i) {
      message[2 + postSaltOffset + i] = IV[i];
    }

    for (int i = 0; i < AAD.length; ++i) {
      message[2 + postSaltOffset + IV.length  + i ] = AAD[i];
    }

    for (int i = 0; i < cipherText.length; ++i) {
      message[2 + postSaltOffset + IV.length + AAD.length + i] = cipherText[i];
    }

    for (int i = 0; i < tag.length; ++i) {
      message[2 + postSaltOffset + IV.length + AAD.length + cipherText.length + i] = tag[i];
    }
  }

  public byte[] entireMessage() {
    return message;
  }

  public int getVersion() {
    return (int) message[0];
  }

  public int getPassword() {
    return (int) message[1];
  }

  public byte[] getSalt() {
    // Salt only exists if PW set.
    if (getPassword() == 0)
      return null;
    return Arrays.copyOfRange(message, 2, 18);
  }

  public byte[] getIv() {
   if (getPassword() != 0)
     return Arrays.copyOfRange(message, 18, 34);
    return Arrays.copyOfRange(message, 2, 18);
  }

  public byte[] getAuth() {
    if (getPassword() != 0)
      return Arrays.copyOfRange(message, 34, 50);
    return Arrays.copyOfRange(message, 18, 34);
  }

  public byte[] getCiphertext() {
    if (getPassword() != 0)
      return Arrays.copyOfRange(message, 50, message.length-16);
    return Arrays.copyOfRange(message, 34, message.length-16);
  }

  public byte[] getTag() {
    return Arrays.copyOfRange(message, message.length-16, message.length);
  }

}
