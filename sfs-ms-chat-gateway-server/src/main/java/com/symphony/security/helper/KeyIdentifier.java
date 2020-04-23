package com.symphony.security.helper;

import com.symphony.security.clientsdk.transport.CiphertextFactory;
import com.symphony.security.clientsdk.transport.ICiphertextTransport;
import com.symphony.security.exceptions.CiphertextTransportIsEmptyException;
import com.symphony.security.exceptions.CiphertextTransportVersionException;
import com.symphony.security.exceptions.InvalidDataException;


import org.apache.commons.codec.binary.Base64;

import java.util.Arrays;

public class KeyIdentifier {

  private byte[] streamId;
  private Long userId;
  private Long rotationId;
  private final Boolean isPublic;

  public Long getRotationId() {
    return rotationId;
  }

  public Boolean getIsPublic() {
    return isPublic;
  }

  public byte[] getStreamId() {
    return streamId;
  }

  public Long getUserId() {
    return userId;
  }

  /**
   * @param streamId threadId associated with the key
   * @param userId userId associated with the key
   * @param rotationId rotationId for which the key must be valid for (each day will be mapped into
   *                   a different key-rotation-period).
   */
  public KeyIdentifier(byte[] streamId, Long userId, Long rotationId) {
    this.streamId = streamId;
    this.userId = userId;
    this.rotationId = rotationId;
    this.isPublic = null;
  }


  /**
   * @param streamId threadId associated with the key
   * @param userId userId associated with the key
   * @param rotationId rotationId for which the key must be valid for (each day will be mapped into
   *                   a different key-rotation-period).
   * @param isPublic whether or not streamId is public or not
   */
  public KeyIdentifier(byte[] streamId, Long userId, Long rotationId, Boolean isPublic) {
    this.streamId = streamId;
    this.userId = userId;
    this.rotationId = rotationId;
    this.isPublic = isPublic;
  }

  /**
   * @param streamId threadId associated with the key
   * @param userId [optional] userId associated with the key, if not specified then it's implied the session
   * @param message Encrypted message from which we can retrieve the rotationId
   * @throws com.sun.media.sound.InvalidDataException
   */
  public KeyIdentifier(byte[] streamId, Long userId, String message, Boolean isPublic) throws
      InvalidDataException, NullPointerException, CiphertextTransportVersionException,
      CiphertextTransportIsEmptyException {
    this.streamId = streamId;
    this.userId = userId;
    ICiphertextTransport ciphertextTransport = CiphertextFactory.getTransport(message);
    this.rotationId = ciphertextTransport.getRotationId();
    this.isPublic = isPublic;
  }

  @Override
  public String toString() {
    String isPublicStr = "private";
    if (isPublic == null)
      isPublicStr = "null";
    else if (isPublic.booleanValue())
      isPublicStr = "public";
    return
         Base64.encodeBase64String(streamId) +
        " (isPublic=" + isPublicStr + ")" +
        " FOR userId=" + userId +
        " @ rotationId=" + rotationId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    KeyIdentifier that = (KeyIdentifier) o;

    if (!rotationId.equals(that.rotationId))

      return false;
    if (!Arrays.equals(streamId, that.streamId))
      return false;
    if (!userId.equals(that.userId))
      return false;

    return true;
  }

  /**
   * Builds a bufid for use in IPersister
   * @return a byte array used to identify this key.
   */
  public byte[] buildBufId() {
    byte[] ret = new byte[streamId.length + 8];
    for( int i=0 ; i < ret.length ; i++ ) {
      ret[i] = 0;
    }
      System.arraycopy(streamId, 0, ret, 0, streamId.length);

    long tmp = rotationId;
    for( int i=0 ; i < 8 ; i++ ) {
      ret[streamId.length + i] = (byte) (0xff & (tmp >> (i * 8)));
    }

    return ret;
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(streamId);
    result = 31 * result + userId.hashCode();
    result = 31 * result + rotationId.hashCode();

    return result;
  }
}
