package com.symphony.security.utils;

import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.DecoderException;
import sun.security.util.DerInputStream;
import sun.security.util.DerValue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringBufferInputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Utility class for creating java.security.Key instances
 *
 * Created by jose abelardo gutierrez on 7/8/15.
 */
public class SecurityKeyUtils {

  private static final String PEM_PUBLIC_START = "-----BEGIN PUBLIC KEY-----";
  private static final String PEM_PUBLIC_END = "-----END PUBLIC KEY-----";

  // PKCS#8 format
  private static final String PEM_PRIVATE_START = "-----BEGIN PRIVATE KEY-----";
  private static final String PEM_PRIVATE_END = "-----END PRIVATE KEY-----";

  // PKCS#1 format
  private static final String PEM_RSA_PRIVATE_START = "-----BEGIN RSA PRIVATE KEY-----";
  private static final String PEM_RSA_PRIVATE_END = "-----END RSA PRIVATE KEY-----";

  /**
   * Creates a RSA Public Key from a PEM String
   *
   * @param pemPublicKey
   * @return
   * @throws GeneralSecurityException
   */
  public static PublicKey parseRSAPublicKey(final String pemPublicKey)
      throws GeneralSecurityException {
    try {
      String publicKeyString = pemPublicKey
          .replace(PEM_PUBLIC_START, "")
          .replace(PEM_PUBLIC_END, "")
          .replace("\\n", "\n")
          .replaceAll("\\s", "");
      byte[] keyBytes = Base64.decode(publicKeyString.getBytes("UTF-8"));
      X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      return keyFactory.generatePublic(spec);

    } catch (DecoderException | InvalidKeySpecException | NoSuchAlgorithmException
        | UnsupportedEncodingException e) {
      throw new GeneralSecurityException(e);
    }
  }

  /**
   * Create a RSA Private Ket from a PEM String. It supports PKCS#1 and PKCS#8 string formats
   *
   * @param pemPrivateKey
   * @return
   * @throws GeneralSecurityException
   */
  public static PrivateKey parseRSAPrivateKey(final String pemPrivateKey)
      throws GeneralSecurityException {
    try {
      if (pemPrivateKey.contains(PEM_PRIVATE_START)) { // PKCS#8 format
        String privateKeyString = pemPrivateKey
            .replace(PEM_PRIVATE_START, "")
            .replace(PEM_PRIVATE_END, "")
            .replace("\\n", "\n")
            .replaceAll("\\s", "");
        byte[] keyBytes = Base64.decode(privateKeyString.getBytes("UTF-8"));
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory fact = KeyFactory.getInstance("RSA");
        return fact.generatePrivate(keySpec);

      } else if (pemPrivateKey.contains(PEM_RSA_PRIVATE_START)) {  // PKCS#1 format
        String privateKeyString = pemPrivateKey
            .replace(PEM_RSA_PRIVATE_START, "")
            .replace(PEM_RSA_PRIVATE_END, "")
            .replace("\\n", "\n")
            .replaceAll("\\s", "");

        DerInputStream derReader = new DerInputStream(Base64.decode(privateKeyString));

        DerValue[] seq = derReader.getSequence(0);

        if (seq.length < 9) {
          throw new GeneralSecurityException("Could not parse a PKCS1 private key.");
        }

        // skip version seq[0];
        BigInteger modulus = seq[1].getBigInteger();
        BigInteger publicExp = seq[2].getBigInteger();
        BigInteger privateExp = seq[3].getBigInteger();
        BigInteger prime1 = seq[4].getBigInteger();
        BigInteger prime2 = seq[5].getBigInteger();
        BigInteger exp1 = seq[6].getBigInteger();
        BigInteger exp2 = seq[7].getBigInteger();
        BigInteger crtCoef = seq[8].getBigInteger();

        RSAPrivateCrtKeySpec keySpec =
            new RSAPrivateCrtKeySpec(modulus, publicExp, privateExp, prime1, prime2, exp1, exp2,
                crtCoef);

        KeyFactory factory = KeyFactory.getInstance("RSA");

        return factory.generatePrivate(keySpec);
      }
      throw new GeneralSecurityException("Not valid private key.");

    } catch (DecoderException | InvalidKeySpecException | NoSuchAlgorithmException
        | IOException e) {
      throw new GeneralSecurityException(e);
    }
  }



  /**
   * Parse an X509 Cert from a String
   *
   * @param certificateString
   * @return
   * @throws GeneralSecurityException
   */
  public static X509Certificate parseX509Certificate(final String certificateString)
      throws GeneralSecurityException {
    try {
      CertificateFactory f = CertificateFactory.getInstance("X.509");
      X509Certificate certificate = (X509Certificate)f.generateCertificate(new ByteArrayInputStream(certificateString.getBytes("UTF-8")));
      return certificate;

    } catch (DecoderException
        | UnsupportedEncodingException e) {
      throw new GeneralSecurityException(e);
    }
  }


}
