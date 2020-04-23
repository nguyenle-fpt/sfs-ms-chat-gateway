package com.symphony.security.clientsdk.transport;

import com.symphony.security.exceptions.InvalidDataException;

import org.apache.commons.codec.binary.Base64;

import java.util.Arrays;

/**
 * Created by jon on 5/25/16.
 */
public class CiphertextTransportV3 implements ICiphertextTransport {

  public static final byte EXPECTED_VERSION = 3;

  static final int EXPECTED_KEYID_SIZE_BYTES = 32;
  static final int EXPECTED_ENCRYTION_MODE_SIZE_BYTES = 1;
  static final int MINIMUM_LENGTH_BYTES =  CiphertextTransportV2.MINIMUM_LENGTH + EXPECTED_KEYID_SIZE_BYTES + EXPECTED_ENCRYTION_MODE_SIZE_BYTES;


  // V3 is based off V2, plus a few additional fields.
  private CiphertextTransportV2 cipherTransportV2;
  private byte[] keyId;
  private byte encryptionMode;

  public CiphertextTransportV3(byte[] bytes) throws InvalidDataException {
    // First thigns first, verify that we have a V3 cipher text.
    if (bytes[0] != EXPECTED_VERSION)
      throw new InvalidDataException("This is not a V3 CiphertextTransport.");

    byte[] vsBytes = Arrays.copyOf(bytes, bytes.length-33);
    vsBytes[0] = 2;
    cipherTransportV2 = new CiphertextTransportV2(vsBytes);
    this.keyId = Arrays.copyOfRange(bytes, bytes.length-33, bytes.length-1);
    this.encryptionMode = bytes[bytes.length-1];
  }

  public CiphertextTransportV3(byte[] ciphertext, byte[] aad, byte[] iv, byte[] tag, int podId,
      long rotationId, byte[] keyId, byte encryptionMode) {
    cipherTransportV2 = new CiphertextTransportV2(ciphertext, aad, iv, tag, podId, rotationId);
    this.keyId = keyId;
    this.encryptionMode = encryptionMode;
  }



  @Override
  public byte getVersion() {
    return EXPECTED_VERSION;
  }

  @Override
  public int getPodId() {
    return cipherTransportV2.getPodId();
  }

  @Override
  public long getRotationId() {
    return cipherTransportV2.getRotationId();
  }

  @Override
  public byte[] getIV() {
    return cipherTransportV2.getIV();
  }

  @Override
  public byte[] getAuthData() {
    return cipherTransportV2.getAuthData();
  }

  @Override
  public byte[] getCiphertext() {
    return cipherTransportV2.getCiphertext();
  }

  @Override
  public byte[] getTag() {
    return cipherTransportV2.getTag();
  }

  @Override
  public byte[] getCiphertextAndTag() {
    return cipherTransportV2.getCiphertextAndTag();
  }

  @Override
  public byte getEncryptionMode() { return encryptionMode; }

  @Override
  public boolean hasKeyId() {
    return true;
  }

  @Override
  public byte[] getRawData() {

    // Based off the v2 packaging,
    byte[] rawV2 = cipherTransportV2.getRawData();
    rawV2[0] = EXPECTED_VERSION;

    // Extend it with our two new fields,
    byte[] rawV3 = new byte[rawV2.length + EXPECTED_KEYID_SIZE_BYTES + EXPECTED_ENCRYTION_MODE_SIZE_BYTES];

    // First, over rawV2.
    System.arraycopy(rawV2,          0, rawV3, 0,                                        rawV2.length);

    // Then append keyId.
    System.arraycopy(keyId,          0, rawV3, rawV2.length,                             EXPECTED_KEYID_SIZE_BYTES);

    // Finally, append encryptionMode.
    rawV3[rawV2.length + EXPECTED_KEYID_SIZE_BYTES] = encryptionMode;
    
    return rawV3;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("CiphertextTransportV3{cipherTransportV2=");
    sb.append(cipherTransportV2.toString());
    sb.append(", keyId=").append(Base64.encodeBase64String(keyId));
    sb.append(", encryptionMode=").append(encryptionMode);
    sb.append('}');
    return sb.toString();
  }

  @Override
  public byte[] getKeyId() {
    return keyId;
  }
}
