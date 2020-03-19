package com.symphony.security.clientsdk.entity;

import com.symphony.security.clientsdk.transport.CiphertextTransportV1;
import com.symphony.security.clientsdk.transport.CiphertextTransportV2;
import com.symphony.security.exceptions.InvalidDataException;
import com.symphony.security.exceptions.SymphonyEncryptionException;
import com.symphony.security.exceptions.SymphonyInputException;
import com.gs.ti.wpt.lc.security.cryptolib.AES;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.ArrayUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Created by serkan on 6/30/15.
 */
public class EntityCryptoHandler {

  public static String encrypt(String plainText,byte[] key)
      throws SymphonyEncryptionException, SymphonyInputException, NoSuchAlgorithmException {

    String inPrefix=plainText.substring(0,1);
    if(!"#".equals(inPrefix) && !"$".equals(inPrefix) && !"?".equals(inPrefix) ){
      throw new SymphonyEncryptionException(plainText + " is not a valid entity.");
    }
    if("?".equals(inPrefix) ){
      throw new SymphonyEncryptionException(plainText + " is already encrypted.");
    }
    String lowercase=plainText.toLowerCase();
    byte[] iv = getIv(lowercase,key);
    byte[] aad = new byte[CiphertextTransportV2.AUTH_DATA_LENGTH];
    byte[] tag = new byte[CiphertextTransportV2.TAG_LENGTH];
    byte[] cipherText= AES.EncryptGCM(lowercase.getBytes(StandardCharsets.UTF_8), aad, key, iv, tag);
    //version|PW|IV|aad|ciphertext|tag
    byte[] versionAndPW={1,0};
    byte[] versionPW_IV=ArrayUtils.addAll(versionAndPW,iv);
    byte[] versionPW_IV_aad=ArrayUtils.addAll(versionPW_IV,aad);
    byte[] versionPW_IV_aad_ciphertext=ArrayUtils.addAll(versionPW_IV_aad,cipherText);
    byte[] all=ArrayUtils.addAll(versionPW_IV_aad_ciphertext,tag);
    String entityPrefix= "?";
    return (entityPrefix + Base64.encodeBase64String(all));
  }

  public static byte[] getIv(String plainText, byte[] key) throws NoSuchAlgorithmException {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    MessageDigest md2 = MessageDigest.getInstance("SHA-256");
    md2.update(key);
    byte[] hashedKey=md2.digest();
    byte[] plainTextBytes=plainText.getBytes(StandardCharsets.UTF_8);
    byte[] ivPreHash=ArrayUtils.addAll(plainTextBytes,hashedKey);
    md.update(ivPreHash);
    byte[] iv32 = md.digest();
    return Arrays.copyOfRange(iv32, 0, 16);
  }

  public static String decrypt(String cipherTextB64, byte[] key)
      throws SymphonyEncryptionException, SymphonyInputException, InvalidDataException {
    String in=cipherTextB64.substring(1);
    CiphertextTransportV1 ciphertextTransportV1=new CiphertextTransportV1(Base64.decodeBase64(in));
    byte[] aad =ciphertextTransportV1.getAuthData();
    byte[] iv= ciphertextTransportV1.getIV();
    byte[] tag=ciphertextTransportV1.getTag();
    byte[] cipherText =ciphertextTransportV1.getCiphertext();
    byte[] plainText=AES.DecryptGCM(cipherText, aad, key, iv, tag);
    return new String(plainText);
  }
}
