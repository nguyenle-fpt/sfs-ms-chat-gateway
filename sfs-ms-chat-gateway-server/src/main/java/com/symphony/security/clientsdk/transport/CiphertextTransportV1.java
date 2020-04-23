package com.symphony.security.clientsdk.transport;

import com.symphony.security.exceptions.InvalidDataException;
import com.symphony.security.utils.Validate;

import org.apache.commons.lang.ArrayUtils;

import java.util.Arrays;

/**
 * Created by sergey on 5/27/15.
 */
@Deprecated
public class CiphertextTransportV1 implements ICiphertextTransport {

  static final byte EXPECTED_VERSION = 1;

  static final int FLAG_PASSWORD = 0x01;
  static final int SALT_LENGTH = 16; // if user PW
  public static final int IV_LENGTH = 16; // AES_BLOCK_SIZE
  public static final int AUTH_DATA_LENGTH = 16;
  public static final int TAG_LENGTH = 16; // like HMAC_SIZE
  static final int HEADER_SIZE = 2;

  static final int MINIMUM_LENGTH_WITH_PASSWORD =
      HEADER_SIZE + SALT_LENGTH + IV_LENGTH + AUTH_DATA_LENGTH + TAG_LENGTH;
  static final int MINIMUM_LENGTH_WITHOUT_PASSWORD =
      HEADER_SIZE + IV_LENGTH + AUTH_DATA_LENGTH + TAG_LENGTH;

  private final byte version;
  private final byte options;
  private final byte[] salt;
  private final byte[] iv;
  private final byte[] authData;
  private final byte[] cipherText;
  private final byte[] tag;
  private final boolean isPasswordBased;

  public CiphertextTransportV1(byte[] cipherText, byte[] aad, byte[] iv, byte[] tag) {
    this.version = EXPECTED_VERSION;
    this.options = 0x00;
    this.salt = null;
    this.iv = iv;
    this.authData = aad;
    this.tag = tag;
    this.isPasswordBased = false;
    this.cipherText = cipherText;
  }

  /**
   * Parses binary data to create an {@code SymphonyCiphertext}.
   *
   * @param data the data to parse
   * @throws com.sun.media.sound.InvalidDataException if the data is not valid
   */
  public CiphertextTransportV1(byte[] data) throws InvalidDataException {
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

    options = data[index++];

    // Test for any invalid flags
    if (options != 0x00 && options != FLAG_PASSWORD) {
      throw new InvalidDataException("Unrecognised bit in the options byte.");
    }

    // If the password bit is set, we can expect salt values
    isPasswordBased = ((options & FLAG_PASSWORD) == FLAG_PASSWORD);

    final int minimumLength =
        (isPasswordBased) ? MINIMUM_LENGTH_WITH_PASSWORD : MINIMUM_LENGTH_WITHOUT_PASSWORD;

    if (data.length < minimumLength) {
      throw new InvalidDataException(String.format("Data must be a minimum length of %d bytes, but found %d bytes.", minimumLength, data.length));
    }

    if (isPasswordBased) {
      salt = new byte[SALT_LENGTH];
      System.arraycopy(data, index, salt, 0, salt.length);
      index += salt.length;
    } else {
      salt = null;
    }

    final int ciphertextLength = data.length - minimumLength;
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

    // Header: [Version | Options]
    byte[] header = new byte[] {EXPECTED_VERSION, 0};

    if (isPasswordBased) {
      header[1] |= FLAG_PASSWORD;
    }

    // Pack result
    final int dataSize;

    if (isPasswordBased) {
      dataSize = header.length + salt.length + iv.length + authData.length + cipherText.length
          + tag.length;
    } else {
      dataSize = header.length + iv.length + authData.length + cipherText.length + tag.length;
    }

    byte[] result = new byte[dataSize];

    System.arraycopy(header, 0, result, 0, header.length);

    if (isPasswordBased) {
      System.arraycopy(salt, 0, result, header.length, salt.length);
      System.arraycopy(iv, 0, result, header.length + salt.length, iv.length);
      System.arraycopy(authData, 0, result,
          header.length + salt.length + iv.length, authData.length);
      System.arraycopy(cipherText, 0, result,
          header.length + salt.length + iv.length + authData.length, cipherText.length);
      System.arraycopy(tag, 0, result, header.length + salt.length + iv.length + authData.length
          + cipherText.length, tag.length);
    } else {
      System.arraycopy(iv, 0, result, header.length, iv.length);
      System.arraycopy(authData, 0, result, header.length + iv.length, authData.length);
      System.arraycopy(cipherText, 0, result,
          header.length + iv.length + authData.length, cipherText.length);
      System.arraycopy(tag, 0, result,
          header.length + iv.length + authData.length + cipherText.length, tag.length);
    }

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
    throw new IllegalStateException("This method is not allowed for CiphertextTransportV1");
  }

  /*
   RotationId is not used in this version
   */
  @Override
  public long getRotationId() {
    throw new IllegalStateException("This method is not allowed for CiphertextTransportV1");
  }

  /**
   * @return the options
   */
  public byte getOptions() {
    return options;
  }

  /**
   * @return the salt
   */
  public byte[] getSalt() {
    return salt;
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
    //In Java the tag is unfortunately added at the end of the ciphertext, so I have added "Java" to the name of function
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
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(cipherText);
    result = prime * result + Arrays.hashCode(salt);
    result = prime * result + Arrays.hashCode(tag);
    result = prime * result + Arrays.hashCode(authData);
    result = prime * result + (isPasswordBased ? 1231 : 1237);
    result = prime * result + Arrays.hashCode(iv);
    result = prime * result + options;
    result = prime * result + version;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    CiphertextTransportV1 other = (CiphertextTransportV1) obj;
    if (!Arrays.equals(cipherText, other.cipherText)) {
      return false;
    }
    if (!Arrays.equals(salt, other.salt)) {
      return false;
    }
    if (!Arrays.equals(tag, other.tag)) {
      return false;
    }
    if (!Arrays.equals(authData, other.authData)) {
      return false;
    }
    if (isPasswordBased != other.isPasswordBased) {
      return false;
    }
    if (!Arrays.equals(iv, other.iv)) {
      return false;
    }
    if (options != other.options) {
      return false;
    }
    if (version != other.version) {
      return false;
    }
    return true;
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

  @Override
  public String toString() {
    return "V1";
  }
}
