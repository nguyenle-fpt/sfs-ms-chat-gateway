package com.symphony.security.clientsdk.entity;


import com.symphony.security.exceptions.InvalidDataException;
import com.symphony.security.utils.Bytes;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.ArrayUtils;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by serkan  on 7/22/16.
 */
public class EntityCiphertextTransportV2 {
  public static final int VERSION_SIZE = 1;

  public static final int ROTATION_ID_SIZE = 8;
  private static final int TOKEN_NO_SIZE = 1;

  public static final int TOKENSIZE = 12;
  public static final int IV_SIZE = 16;
  public static final int TAG_SIZE = 16;
  public static final int MAX_TOKEN_NUMBER = 4;

  public static final String ENTITY_PREFIX = "!";

  private byte version;
  private long rotationId;
  private byte tokenNumber;
  private List<byte[]> tokens;
  private byte[] iv;
  private byte[] tag;
  private byte[] cipherText;

  private int hash = 0;

  // 1 byte version | 8 byte rotationId   |  token token token | 16 byte iv| 16 byte tag | ciphertext | 1 byte token number
  public EntityCiphertextTransportV2(String text) throws InvalidDataException, DecoderException {
    if(text == null || text.trim().isEmpty()) {
      throw new InvalidDataException("CipherText cannot be null");
    }
    if(!text.substring(0,1).equals(ENTITY_PREFIX)) {
      throw new InvalidDataException("Entity should start with " + ENTITY_PREFIX);
    }
    String rawStr =  text.substring(1);

    byte[] raw = Base64.decodeBase64(rawStr);
    int index = 0;
    byte[] tmp;
    version = raw[index++];
    tmp = Arrays.copyOfRange(raw, index, index + ROTATION_ID_SIZE);
    index += ROTATION_ID_SIZE;
    rotationId = Bytes.toLong(tmp);
    tmp = Arrays.copyOfRange(raw, raw.length - TOKEN_NO_SIZE, raw.length);
    tokenNumber = tmp[0];
    if(tokenNumber > MAX_TOKEN_NUMBER || tokenNumber < 0){
      throw new InvalidDataException("TokenNumber is larger than maximum token size:" +
          MAX_TOKEN_NUMBER);
    }
    tokens = new LinkedList<>();
    for(byte i=0; i<tokenNumber; i++){
      tmp = Arrays.copyOfRange(raw, index, index + TOKENSIZE);
      index += TOKENSIZE;
      tokens.add(tmp);
    }
    iv = Arrays.copyOfRange(raw, index, index + IV_SIZE);
    index += IV_SIZE;
    tag = Arrays.copyOfRange(raw, index, index + TAG_SIZE);
    index += TAG_SIZE;
    cipherText = Arrays.copyOfRange(raw, index, raw.length - TOKEN_NO_SIZE);

  }

  public EntityCiphertextTransportV2(byte version, long rotationId,
      List<byte[]> tokens, byte[] iv, byte[] tag, byte[] cipherText) throws InvalidDataException {
    if(tokens == null || iv == null || tag == null || cipherText == null) {
      throw new InvalidDataException("None of the values can be null");
    }

    if(iv.length != IV_SIZE || tag.length != TAG_SIZE) {
      throw new InvalidDataException("tag or iv size are not correct.");
    }

    this.version = version;
    this.rotationId = rotationId;
    this.tokenNumber = (byte) tokens.size();
    this.tokens = tokens;
    this.iv = iv;
    this.tag = tag;
    this.cipherText = cipherText;
  }

  public byte getVersion() {
    return version;
  }

  public long getRotationId() {
    return rotationId;
  }

  public int getTokenNumber() {
    return tokenNumber;
  }

  public List<byte[]> getTokens() {
    return tokens;
  }

  public byte[] getIv() {
    return iv;
  }

  public byte[] getTag() {
    return tag;
  }

  public byte[] getCipherText() {
    return cipherText;
  }

  public String constructEntityString() {
    byte[] entityBytes = getEntityBytes();
    return (ENTITY_PREFIX + Base64.encodeBase64String(entityBytes));
  }

  public String constructEntityTokenPrefixString() {
    byte[] prefixBytes = getEntityTokenBytes();
    return (ENTITY_PREFIX + Base64.encodeBase64String(prefixBytes));
  }

  public byte[] getEntityTokenBytes() {
    byte[] out = {version};
    out = ArrayUtils.addAll(out, Bytes.toBytes(rotationId));
    for(byte[] token: tokens) {
      out = ArrayUtils.addAll(out, token);
    }
    return out;
  }

  public byte[] getEntityBytes() {
    byte[] out = getEntityTokenBytes();
    out = ArrayUtils.addAll(out, getEntityWithoutTokenPrefixBytes());
    return out;
  }

  public byte[] getEntityWithoutTokenPrefixBytes() {
    byte[] out = {};
    byte[] tokenNumberByteArray = {tokenNumber};
    out = ArrayUtils.addAll(out, iv);
    out = ArrayUtils.addAll(out, tag);
    out = ArrayUtils.addAll(out, cipherText);
    out = ArrayUtils.addAll(out, tokenNumberByteArray);
    return out;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) { return true; }
    if (o == null || getClass() != o.getClass()) { return false; }

    EntityCiphertextTransportV2 that = (EntityCiphertextTransportV2) o;

    if (version != that.version) { return false; }
    if (rotationId != that.rotationId) { return false; }
    if (tokens != null ? !tokens.equals(that.tokens) : that.tokens != null) { return false; }
    if (!Arrays.equals(iv, that.iv)) { return false; }
    if (!Arrays.equals(tag, that.tag)) { return false; }
    return Arrays.equals(cipherText, that.cipherText);
  }

  @Override
  public int hashCode() {
    int result = hash;
    if(result == 0) {
      result = (int) version;
      result = 31 * result + (int) (rotationId ^ (rotationId >>> 32));
      result = 31 * result + (tokens != null ? tokens.hashCode() : 0);
      result = 31 * result + Arrays.hashCode(iv);
      result = 31 * result + Arrays.hashCode(tag);
      result = 31 * result + Arrays.hashCode(cipherText);
      hash = result;
    }
    return result;
  }
}
