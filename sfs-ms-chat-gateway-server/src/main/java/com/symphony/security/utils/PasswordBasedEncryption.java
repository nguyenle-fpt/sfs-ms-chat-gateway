package com.symphony.security.utils;

import com.symphony.security.exceptions.SymphonyEncryptionException;

import org.apache.commons.codec.binary.Base64;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
/**
 * Created by matt.harper on 2/2/17.
 */
public class PasswordBasedEncryption {


  public static final int ITERATIONS = 10000;

  public static final int SALT_SIZE = 8;
  public static final int KEY_SIZE = 256;

  private static final int IV_SIZE = 16;
  public static final String PBKDF_2_WITH_HMAC_SHA_1 = "PBKDF2WithHmacSHA1";
  public static final String AES_CBC_PKCS5_PADDING = "AES/CBC/PKCS5Padding";
  public static final String AES = "AES";
  public static final String UTF_8 = "UTF-8";


  private PasswordBasedEncryption(){
  }

  public static String encrypt(char[] password, String plainText) throws SymphonyEncryptionException {
    notNull(password, "password");
    notNull(plainText, "plainText");
    try {
      byte[] saltBytes = randomBytes(SALT_SIZE);
      byte[] ivBytes = randomBytes(IV_SIZE);

      SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF_2_WITH_HMAC_SHA_1);
      PBEKeySpec spec = new PBEKeySpec(password, saltBytes, ITERATIONS, KEY_SIZE);

      SecretKey secretKey = factory.generateSecret(spec);
      SecretKeySpec secret = new SecretKeySpec(secretKey.getEncoded(), AES);

      Cipher cipher = Cipher.getInstance(AES_CBC_PKCS5_PADDING);
      cipher.init(Cipher.ENCRYPT_MODE, secret, new IvParameterSpec(ivBytes));

      byte[] encryptedTextBytes = cipher.doFinal(plainText.getBytes(UTF_8));

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      outputStream.write(saltBytes);
      outputStream.write(ivBytes);
      outputStream.write(encryptedTextBytes);
      outputStream.flush();

      return Base64.encodeBase64String(outputStream.toByteArray());
    } catch (Exception ex) {
      throw new SymphonyEncryptionException(ex);
    }
  }

  private static void notNull(Object parameter, String parameterName) {
    if( parameter == null ){
      throw new IllegalArgumentException(parameterName + " is a required parameter");
    }
  }

  public static String decrypt(char[] password, String encryptedText) throws SymphonyEncryptionException {
    try {
      byte[] decoded = Base64.decodeBase64(encryptedText);
      byte[] saltBytes = Arrays.copyOfRange(decoded, 0, SALT_SIZE);
      byte[] ivBytes = Arrays.copyOfRange(decoded, SALT_SIZE, SALT_SIZE + IV_SIZE);
      byte[] cipherText = Arrays.copyOfRange(decoded, SALT_SIZE + IV_SIZE, decoded.length);

      SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF_2_WITH_HMAC_SHA_1);
      PBEKeySpec spec = new PBEKeySpec(password, saltBytes, ITERATIONS, KEY_SIZE);

      SecretKey secretKey = factory.generateSecret(spec);
      SecretKeySpec secret = new SecretKeySpec(secretKey.getEncoded(), AES);

      Cipher cipher = Cipher.getInstance(AES_CBC_PKCS5_PADDING);
      cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(ivBytes));

      byte[] decryptedTextBytes = cipher.doFinal(cipherText);

      return new String(decryptedTextBytes, UTF_8);
    } catch (Exception ex) {
      throw new SymphonyEncryptionException(ex);
    }
  }

  public static byte[] randomBytes(final int lengthBytes) {
    final byte[] salt = new byte[lengthBytes];
    SecureRandom random = new SecureRandom();
    synchronized (random) {
      random.nextBytes(salt);
    }
    return salt;
  }

}
