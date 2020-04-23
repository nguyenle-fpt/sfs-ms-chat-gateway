package com.symphony.security.clientsdk.entity;

import com.symphony.security.exceptions.InvalidDataException;
import com.symphony.security.exceptions.SymphonyEncryptionException;
import com.symphony.security.exceptions.SymphonyInputException;
import com.symphony.security.utils.CryptoHashUtils;
import com.gs.ti.wpt.lc.security.cryptolib.AES;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by serkan  on 7/21/16.
 */
public class EntityCryptoHandlerV2 {

  private static final byte EXPECTED_VERSION = 2;
  private static final long ROTATION_ID = 0;
  private static final int[] HASH_POINTS = {2, 4, 7, 12};
  protected static final byte[] AAD = new byte[16];


  public static String encrypt(String plainText, byte[] key)
      throws SymphonyEncryptionException, SymphonyInputException, InvalidDataException {
    if(plainText == null || plainText.trim().isEmpty() ){
      throw new SymphonyEncryptionException("Plaintext cannot be null");
    }
    if(key == null ){
      throw new SymphonyEncryptionException("Key cannot be null");
    }
    String inPrefix = plainText.substring(0,1);
    if(!"#".equals(inPrefix) && !"$".equals(inPrefix) && !"?".equals(inPrefix) && !EntityCiphertextTransportV2.ENTITY_PREFIX.equals(inPrefix) ){
      throw new SymphonyEncryptionException(plainText + " is not a valid entity.");
    }
    if("?".equals(inPrefix) || EntityCiphertextTransportV2.ENTITY_PREFIX.equals(inPrefix) ){
      throw new SymphonyEncryptionException(plainText + " is already encrypted.");
    }
    String lowercase = plainText.toLowerCase();
    byte[] iv;

    try {
      iv = getIv(lowercase, key);
    } catch (NoSuchAlgorithmException e) {
      throw new SymphonyEncryptionException(e);
    }

    byte[] tag = new byte[EntityCiphertextTransportV2.TAG_SIZE];
    byte[] cipherText = AES.EncryptGCM(lowercase.getBytes(StandardCharsets.UTF_8), AAD,key,iv,tag);

    List<byte[]> tokens = getTokens(lowercase, key);

    EntityCiphertextTransportV2 entityCiphertextTransportV2 =
        new EntityCiphertextTransportV2(EXPECTED_VERSION, ROTATION_ID, tokens,iv, tag, cipherText);

    return entityCiphertextTransportV2.constructEntityString();
  }

  private static List<byte[]> getTokens(String lowercase, byte[] key) {
    List<byte[]> tokens = new LinkedList<>();
    for(int hashpoint : HASH_POINTS){
      if(lowercase.length() > hashpoint) {
        String plainToken = lowercase.substring(0,hashpoint+1);
        byte[] fullToken = CryptoHashUtils.HMACxSHA256(key,plainToken.getBytes(StandardCharsets.UTF_8));
        byte[] token = Arrays.copyOfRange(fullToken, 0, EntityCiphertextTransportV2.TOKENSIZE);
        tokens.add(token);
      }
    }
    return tokens;
  }

  public static String getTokenPrefix(String plaintext, byte[] key)
      throws InvalidDataException, SymphonyInputException, SymphonyEncryptionException,
      DecoderException {
    String encrypted = encrypt(plaintext,key);
    EntityCiphertextTransportV2 transportV2 = new EntityCiphertextTransportV2(encrypted);
    return transportV2.constructEntityTokenPrefixString();
  }

  private static byte[] getIv(String lowercase, byte[] key) throws NoSuchAlgorithmException {
    byte[] hmacKey = CryptoHashUtils.sha256digest(key);
    byte[] iv32 = CryptoHashUtils.HMACxSHA256(hmacKey, lowercase.getBytes(StandardCharsets.UTF_8));
    return Arrays.copyOfRange(iv32,0,EntityCiphertextTransportV2.IV_SIZE);
  }

  public static String decrypt(String cipherText, byte[] key)
      throws SymphonyEncryptionException, SymphonyInputException {
    if(cipherText == null || cipherText.trim().isEmpty()){
      throw new SymphonyEncryptionException("CipherText cannot be null");
    }
    if(key == null ){
      throw new SymphonyEncryptionException("Key cannot be null");
    }
    EntityCiphertextTransportV2 entityCiphertextTransportV2;
    try {
      entityCiphertextTransportV2 = new EntityCiphertextTransportV2(cipherText);
    } catch(Exception e) {
      throw new SymphonyEncryptionException(e);
    }
    byte[] cipherTextByte = entityCiphertextTransportV2.getCipherText();
    byte[] iv = entityCiphertextTransportV2.getIv();
    byte[] tag = entityCiphertextTransportV2.getTag();
    byte[] out = AES.DecryptGCM(cipherTextByte,AAD,key,iv,tag);
    return StringUtils.newStringUtf8(out);
  }
}
