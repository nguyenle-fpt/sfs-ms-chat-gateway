package com.symphony.security.utils;

import com.symphony.security.exceptions.SymphonyPEMFormatException;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import sun.security.x509.X509CertInfo;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;

/**
 * @author ivan
 *         Created by ivan on 6/3/14.
 */
public class Converter {

  private final static char[] HEX_ARRAY = "0123456789abcdef".toCharArray();


  public static String bytesToHex(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = HEX_ARRAY[v >>> 4];
      hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
    }
    return new String(hexChars);
  }

  public static byte[] hexStringToBytes(String hexString) {
    int len = hexString.length() / 2;
    byte[] result = new byte[len];
    for (int i = 0; i < len; i++)
      result[i] = Integer.valueOf(hexString.substring(2 * i, 2 * i + 2), 16).byteValue();
    return result;
  }

  public static byte[] charsToBytes(char[] chars) {
    int length = chars.length;
    byte[] result = new byte[length];
    for (int i = 0; i < length; i++) {
      result[i] = (byte) chars[i];
    }

    return result;
  }

  public static char[] bytesToChars(byte[] bytes) {
    int length = bytes.length;
    char[] result = new char[length];
    for (int i = 0; i < length; i++) {
      result[i] = (char) bytes[i];
    }

    return result;
  }

  public static String bytesToString(byte[] bytes) {
    return String.valueOf(bytesToChars(bytes));
  }

  public static String toPemString(Object key) throws SymphonyPEMFormatException {
    StringWriter stringWriter = new StringWriter();
    JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter);
    try {
      pemWriter.writeObject(key);
      pemWriter.flush();
    } catch (IOException e) {
      throw new SymphonyPEMFormatException();
    }
    return stringWriter.toString();
  }

  public static PrivateKey getPrivateKeyFromPem(String privateKeyPemString) throws SymphonyPEMFormatException {
    Security.addProvider(new BouncyCastleProvider());
    PrivateKey privateKey = null;
    try {
      StringReader stringReaderPrivate = new StringReader(privateKeyPemString);
      PEMParser pemParser = new PEMParser(stringReaderPrivate);
      Object o = pemParser.readObject();
      JcaPEMKeyConverter myConverter = new JcaPEMKeyConverter();
      if (o instanceof PEMKeyPair) {
        privateKey = myConverter.getKeyPair((PEMKeyPair) o).getPrivate();
      }
    } catch (IOException e) {
      throw new SymphonyPEMFormatException();
    }
    return privateKey;
  }

  public static PublicKey getPublicKeyFromPem(String publicKeyPemString) throws SymphonyPEMFormatException {
    Security.addProvider(new BouncyCastleProvider());
    PublicKey publicKey = null;
    try {
      StringReader stringReaderPublic = new StringReader(publicKeyPemString);
      PEMParser pemParser = new PEMParser(stringReaderPublic);
      Object o = pemParser.readObject();
      JcaPEMKeyConverter myConverter = new JcaPEMKeyConverter();
      if (o instanceof SubjectPublicKeyInfo) {
        publicKey = myConverter.getPublicKey((SubjectPublicKeyInfo) o);
      }
    } catch (IOException e) {
      throw new SymphonyPEMFormatException();
    }
    return publicKey;
  }

  public static X509Certificate getCertificateFromPem(String certPemString) throws SymphonyPEMFormatException {
    Security.addProvider(new BouncyCastleProvider());
    X509Certificate cert = null;
    try {
      StringReader stringReaderPublic = new StringReader(certPemString);
      PEMParser pemParser = new PEMParser(stringReaderPublic);
      Object o = pemParser.readObject();
      JcaX509CertificateConverter myConverter = new JcaX509CertificateConverter();
      if (o instanceof X509CertificateHolder) {
        cert = myConverter.getCertificate((X509CertificateHolder) o);
      }
    } catch (IOException | CertificateException e) {
      throw new SymphonyPEMFormatException();
    }
    return cert;
  }

  public static X509CRL getCRLFromPem(String crlPemString) throws SymphonyPEMFormatException {
    Security.addProvider(new BouncyCastleProvider());
    X509CRL crl = null;
    try {
      StringReader stringReaderPublic = new StringReader(crlPemString);
      PEMParser pemParser = new PEMParser(stringReaderPublic);
      Object o = pemParser.readObject();
      JcaX509CRLConverter myConverter = new JcaX509CRLConverter();
      if (o instanceof X509CRLHolder) {
        crl = myConverter.getCRL((X509CRLHolder) o);
      }
    } catch (IOException | CRLException e) {
      throw new SymphonyPEMFormatException();
    }
    return crl;
  }

  public static PKCS10CertificationRequest getCertificateRequestFromPem(String reqPemString) throws SymphonyPEMFormatException {
    Security.addProvider(new BouncyCastleProvider());
    PKCS10CertificationRequest req = null;
    try {
      StringReader stringReaderPublic = new StringReader(reqPemString);
      PEMParser pemParser = new PEMParser(stringReaderPublic);
      Object o = pemParser.readObject();
      if (o instanceof PKCS10CertificationRequest) {
        req = (PKCS10CertificationRequest)o;
      }
    } catch (IOException e) {
      throw new SymphonyPEMFormatException(e.getMessage());
    }
    return req;
  }

  public static PublicKey getPublicKeyFromPKCS10CertificationRequest(PKCS10CertificationRequest req) throws SymphonyPEMFormatException {
    Security.addProvider(new BouncyCastleProvider());
    PublicKey publicKey = null;
    if (req != null) {
      try {
        SubjectPublicKeyInfo subjectPublicKeyInfo = req.getSubjectPublicKeyInfo();
        RSAKeyParameters rsa = (RSAKeyParameters) PublicKeyFactory.createKey(subjectPublicKeyInfo);
        RSAPublicKeySpec rsaSpec = new RSAPublicKeySpec(rsa.getModulus(), rsa.getExponent());
        KeyFactory kf = KeyFactory.getInstance("RSA");
        publicKey = kf.generatePublic(rsaSpec);
      } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
        throw new SymphonyPEMFormatException(e.getMessage());
      }
    }
    return publicKey;
  }

  public static String getSubjectFromPKCS10CertificationRequest(PKCS10CertificationRequest req) {
    Security.addProvider(new BouncyCastleProvider());
    String subject = null;
    if (req != null) {
      subject = req.getSubject().toString();
    }
    return subject;
  }

  public static X509CertInfo getSubjectInfoFromPemCSR(String reqPemString) throws SymphonyPEMFormatException {
    PKCS10CertificationRequest pkcs10 = getCertificateRequestFromPem(reqPemString);
    X509CertInfo subjectInfo = null;
    if (pkcs10 != null) {
      subjectInfo = new X509CertInfo();
    }
    return subjectInfo;
  }

  public static int getJavaVersion() {
    String[] javaVersions = System.getProperty("java.version").split("\\.");
    int j1 = Integer.valueOf(javaVersions[0]);
    int j2 = Integer.valueOf(javaVersions[1]);
    int javaVersion = (j1 > 1)? j1: (j1 == 1)? j2 : 0;
    return javaVersion;
  }

  public static String getSubjectFromPemPKCS10(String pkcs10) throws Exception {
    String p10WithoutHeader = pkcs10.replace("-----BEGIN CERTIFICATE REQUEST-----\n", "");
    String p10Naked = p10WithoutHeader.replace("\n-----END CERTIFICATE REQUEST-----", "");
    byte[] bytes = Base64.decodeBase64(p10Naked);
    Class<?> pkcs10Class = Class.forName("sun.security.pkcs10.PKCS10");
    Constructor<?> pkcs10Constructor = pkcs10Class.getConstructor(byte[].class);
    Object o = pkcs10Constructor.newInstance(bytes);  // This method verifies CSR's signature
    Method getSubjectNameMethod = pkcs10Class.getMethod("getSubjectName", null);
    return getSubjectNameMethod.invoke(o).toString();
  }

  public static PublicKey getSubjectPublicKeyInfoFromPemPKCS10(String pkcs10) throws SymphonyPEMFormatException {
    try {
      String p10WithoutHeader = pkcs10.replace("-----BEGIN CERTIFICATE REQUEST-----\n", "");
      String p10Naked = p10WithoutHeader.replace("\n-----END CERTIFICATE REQUEST-----", "");
      byte[] bytes = Base64.decodeBase64(p10Naked);
      Class<?> pkcs10Class = Class.forName("sun.security.pkcs10.PKCS10");
      Constructor<?> pkcs10Constructor = pkcs10Class.getConstructor(byte[].class);
      Object o = pkcs10Constructor.newInstance(bytes);  // This method verifies CSR's signature
      Method getSubjectPublicKeyInfoMethod = pkcs10Class.getMethod("getSubjectPublicKeyInfo", null);
      return (PublicKey) getSubjectPublicKeyInfoMethod.invoke(o);
    } catch (Exception e) {
      throw new SymphonyPEMFormatException("PKCS10 generation failure: '"+e.getMessage()+"'", e);
    }
  }

  public static Key getKeyFromPem(String pemString) throws SymphonyPEMFormatException {
    Security.addProvider(new BouncyCastleProvider());
    Key key = null;
    try {
      StringReader stringReaderPublic = new StringReader(pemString);
      PEMParser pemParser = new PEMParser(stringReaderPublic);
      Object o = pemParser.readObject();
      JcaPEMKeyConverter myConverter = new JcaPEMKeyConverter();
      if (o instanceof SubjectPublicKeyInfo) {
        key = myConverter.getPublicKey((SubjectPublicKeyInfo) o);
      } else if (o instanceof PEMKeyPair) {
        key = myConverter.getKeyPair((PEMKeyPair) o).getPrivate();
      }
    } catch (IOException e) {
      throw new SymphonyPEMFormatException();
    }
    return key;
  }
}
