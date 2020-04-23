package com.symphony.security.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cryptographic hash SHA-256
 * <p>
 * </p>Created by Ivan Rylach on 4/4/16
 *
 * @author i@symphony.com
 */
public class CryptoHashUtils {

  private static Logger LOG = LoggerFactory.getLogger(CryptoHashUtils.class);
  private static final int BUFFER_SIZE = 8 * 1024;
  private static final ObjectWriter WRITER = new ObjectMapper().writer();
  private static final byte[] FINGER_PRINT_DATA_FOR_KEY_ID ="KeyFingerprint".getBytes();
  //TODO: this is duplicate with HSMNamingUtils - need to resolve cyclic dependency
  public final static String CEK_PREFIX = "content-export-key-";

  /**
   * Calculates SHA-256 hash of a given byte array
   *
   * @param data
   *     byte array to calculate hash
   * @return byte array of hash (32 bytes)
   */
  public static byte[] sha256digest(byte[] data) throws NoSuchAlgorithmException {
    return getSha256MessageDigestInstance().digest(data);
  }

  /**
   * Calculates SHA-256 hash of a given byte array
   *
   * @param inputStream
   *     input stream of bytes to calculate hash
   * @return byte array of hash (32 bytes)
   */
  public static byte[] sha256digest(InputStream inputStream) throws IOException, NoSuchAlgorithmException {

    byte[] buffer = new byte[BUFFER_SIZE];
    MessageDigest messageDigest = getSha256MessageDigestInstance();

    try (DigestInputStream digestInputStream = new DigestInputStream(inputStream, messageDigest)) {

      while (digestInputStream.read(buffer) != -1) {}
      return messageDigest.digest();

    } catch (IOException e) {
      LOG.error("Failed to sha256digest stream of bytes", e);
      throw new IOException("Failed to sha256digest stream of bytes");
    }

  }

  private static MessageDigest getSha256MessageDigestInstance() throws NoSuchAlgorithmException {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      LOG.error(e.getMessage(), e);
      throw new NoSuchAlgorithmException(e);
    }
  }

  @Deprecated
  public static byte[] hmacSha256digest(byte[] plainTextBytes, byte[] secret) throws NoSuchAlgorithmException, InvalidKeyException {
    return HMACxSHA256(secret, plainTextBytes);
  }

  public static byte[] getFingerPrintDataForKeyId() {
    return FINGER_PRINT_DATA_FOR_KEY_ID;
  }

  public static byte[] keyFingerprint(byte[] key) {
    return Base64.decodeBase64(keyFingerprint(Base64.encodeBase64String(key)));
  }

  public static String keyFingerprint(String key) {
    byte[] fingerprint = HMACxSHA256(Base64.decodeBase64(key), FINGER_PRINT_DATA_FOR_KEY_ID);
    String finterprintStr = "fingerprint-failed";
    if (fingerprint != null)
      finterprintStr = Base64.encodeBase64String(fingerprint);
    return finterprintStr;
  }

  public static byte[] HMACxSHA256(byte[] key, byte[] message) {
    try {
      if (key == null) {
        LOG.error("No key, or invalid key length on HMACxSHA256 ");
        return null;
      }

      Mac sha256_HMAC;
      try {
        sha256_HMAC = Mac.getInstance("HmacSHA256");
      } catch (NoSuchAlgorithmException e) {
        LOG.error("No HmacSHA256 alg found, ", e);
        return null;
      }
      SecretKeySpec secret_key = new SecretKeySpec(key, "HmacSHA256");
      try {
        sha256_HMAC.init(secret_key);
      } catch (InvalidKeyException e) {
        LOG.error("Invalid key in HmacSHA256, ", e);
        return null;
      }

      return sha256_HMAC.doFinal(message);
    } catch (Exception e) {
      LOG.error("Failed to get HMACxSHA256 : ", e);
      return null;
    }
  }

  /**
   * Get an unique hash that is consistent across all KMs of the same version.<br/>
   * Should take in consideration all keyFingerPrints but  content export key.
   * @param fingerPrints
   * @return
   */
  public static String getHash(Map<String, String> fingerPrints) {
    //to avoid any serialization error default to something simple (like map size)
    Map<String, Object> resultTree = new TreeMap<>();

    String hash = String.valueOf(fingerPrints.size());
    //remove content export keyfingerprints from report
    Iterator it = fingerPrints.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String,Object> pair = (Map.Entry) it.next();
      if(!pair.getKey().contains(CEK_PREFIX)) {
        resultTree.put(pair.getKey(), pair.getValue());
      }
    }

    try {
      hash = WRITER.writeValueAsString(resultTree);
      hash = DigestUtils.sha256Hex(hash);
    } catch (Exception ex) {
      LOG.error("hash could not be calculated.");
    }

    return hash;
  }

}
