package com.symphony.security.clientsdk.transport;

import com.symphony.security.utils.Bytes;
import com.symphony.security.utils.Validate;
import com.symphony.security.exceptions.InvalidDataException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.ArrayUtils;

import java.util.Arrays;

/**
 * Created by sergey on 5/27/15.
 */
public class CiphertextTransportV2 implements ICiphertextTransport {

  static final byte EXPECTED_VERSION = 2;

  static final int HEADER_SIZE = 1; // ONLY VERSION HERE, SO 1 byte
  static final int POD_ID_LENGTH = 4;
  public static final int ROTATION_ID_LENGTH = 8;
  public static final int IV_LENGTH = 16;
  public static final int AUTH_DATA_LENGTH = 16;
  public static final int TAG_LENGTH = 16; // like HMAC_SIZE

  static final int MINIMUM_LENGTH =
      HEADER_SIZE + POD_ID_LENGTH + ROTATION_ID_LENGTH + IV_LENGTH + AUTH_DATA_LENGTH + TAG_LENGTH;

  private final byte version;
  private final byte[] podId;
  private final byte[] rotationId;
  private final byte[] iv;
  private final byte[] authData;
  private final byte[] cipherText;
  private final byte[] tag;

  public CiphertextTransportV2(byte[] cipherText, byte[] aad, byte[] iv, byte[] tag, int podId, long rotationId) {
    this.version = EXPECTED_VERSION;
    this.iv = iv;
    this.authData = aad;
    this.tag = tag;
    this.cipherText = cipherText;
    this.podId = Bytes.toBytes(podId);
    this.rotationId = Bytes.toBytes(rotationId);

  }

  /**
   * Parses binary data to create an {@code SymphonyCiphertext}.
   *
   * @param data the data to parse
   * @throws com.sun.media.sound.InvalidDataException if the data is not valid
   */
  public CiphertextTransportV2(byte[] data) throws InvalidDataException {
    Validate.notNull(data, "Data cannot be null.");

    // Need the header to be able to determine the length
    if (data.length < HEADER_SIZE) {
      throw new InvalidDataException("Not enough data to read header.");
    }

    int index = 0;
    version = data[index++];

    if (version != EXPECTED_VERSION) {
      throw new InvalidDataException(String.format("Expected version %d but found %d.", EXPECTED_VERSION, version));
    }

    if (data.length < MINIMUM_LENGTH) {
      throw new InvalidDataException(String.format("Data must be a minimum length of %d bytes, but found %d bytes.", MINIMUM_LENGTH, data.length));
    }

    final int ciphertextLength = data.length - MINIMUM_LENGTH;

    podId = new byte[POD_ID_LENGTH];
    System.arraycopy(data, index, podId, 0, podId.length);
    index += podId.length;

    rotationId = new byte[ROTATION_ID_LENGTH];
    System.arraycopy(data, index, rotationId, 0, rotationId.length);
    index += rotationId.length;

    iv = new byte[IV_LENGTH];
    System.arraycopy(data, index, iv, 0, iv.length);
    index += iv.length;

    authData = new byte[AUTH_DATA_LENGTH];
    System.arraycopy(data, index, authData, 0, authData.length);
    index += authData.length;

    cipherText = new byte[ciphertextLength];
    System.arraycopy(data, index, cipherText, 0, ciphertextLength);
    index += ciphertextLength;

    tag = new byte[TAG_LENGTH];
    System.arraycopy(data, index, tag, 0, tag.length);

  }


  /**
   * Returns the cipherText, packaged as a byte array.
   *
   * @return the byte array
   */
  @Override
  public byte[] getRawData() {
    // Pack result
    final int dataSize =
        HEADER_SIZE + podId.length + rotationId.length + iv.length + authData.length
            + cipherText.length + tag.length;


    byte[] result = new byte[dataSize];
    byte[] versionBytes = new byte[1];
    versionBytes[0] =version;
    System.arraycopy(versionBytes, 0, result, 0, HEADER_SIZE);
    System.arraycopy(podId, 0, result, HEADER_SIZE, podId.length);
    System.arraycopy(rotationId, 0, result, HEADER_SIZE + podId.length, rotationId.length);
    System.arraycopy(iv, 0, result, HEADER_SIZE + podId.length + rotationId.length, iv.length);

    System.arraycopy(authData, 0, result,
        HEADER_SIZE + podId.length + rotationId.length + iv.length, authData.length);

    System.arraycopy(cipherText, 0, result,
        HEADER_SIZE + podId.length + rotationId.length + iv.length
            + authData.length, cipherText.length);
    System.arraycopy(tag, 0, result,
        HEADER_SIZE + podId.length + rotationId.length + iv.length + authData.length
            + cipherText.length, tag.length);

    return result;
  }


  /**
   * @return the version
   */
  @Override
  public byte getVersion() {
    return version;
  }


  /*
  PodId is not used in this version
   */
  @Override
  public int getPodId() {
    return Bytes.toInt(podId, 0, POD_ID_LENGTH);
  }

  /*
   RotationId is not used in this version
   */
  @Override
  public long getRotationId() {
    return Bytes.toLong(rotationId, 0, ROTATION_ID_LENGTH);
  }

  /**
   * @return the iv
   */
  @Override
  public byte[] getIV() {
    return iv;
  }

  /**
   * In Java the tag is unfortunately added at the end of the ciphertext, so I have added "Java" to the name of function
   *
   * @return the cipherText
   */
  @Override
  public byte[] getCiphertextAndTag() {
    return ArrayUtils.addAll(cipherText, tag);
  }


  /**
   * @return the cipherText
   */
  @Override
  public byte[] getCiphertext() {
    return cipherText;
  }


  /**
   * @return the tag
   */
  @Override
  public byte[] getTag() {
    return tag;
  }

  /**
   * @return the authData
   */
  @Override
  public byte[] getAuthData() {
    return authData;
  }

  @Override
  public int hashCode() {
    int result = (int) version;
    result = 31 * result + Arrays.hashCode(podId);
    result = 31 * result + Arrays.hashCode(rotationId);
    result = 31 * result + Arrays.hashCode(iv);
    result = 31 * result + Arrays.hashCode(authData);
    result = 31 * result + Arrays.hashCode(cipherText);
    result = 31 * result + Arrays.hashCode(tag);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    CiphertextTransportV2 that = (CiphertextTransportV2) o;

    if (version != that.version)
      return false;
    if (!Arrays.equals(podId, that.podId))
      return false;
    if (!Arrays.equals(rotationId, that.rotationId))
      return false;
    if (!Arrays.equals(iv, that.iv))
      return false;
    if (!Arrays.equals(authData, that.authData))
      return false;
    if (!Arrays.equals(cipherText, that.cipherText))
      return false;
    return Arrays.equals(tag, that.tag);

  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("CiphertextTransportV2{version=");
    sb.append(getVersion());
    sb.append(", podId=").append(getPodId());
    sb.append(", rotationId=").append(getRotationId());
    sb.append(", iv=").append(Base64.encodeBase64String(iv));
    sb.append(", authData=").append(Base64.encodeBase64String(authData));
    sb.append(", cipherText=").append(Base64.encodeBase64String(cipherText));
    sb.append(", tag=").append(Base64.encodeBase64String(tag));
    sb.append('}');
    return sb.toString();
  }

  @Override
  public byte[] getKeyId() {
    return null;
  }

  @Override
  public byte getEncryptionMode() {
    return CiphertextTransportEncryptionMode.AES_GCM;
  }

  @Override
  public boolean hasKeyId() {
    return false;
  }
}
