package com.symphony.security.model;


import com.symphony.security.exceptions.ClientCertException;
import com.symphony.security.exceptions.SymphonyPEMFormatException;
import com.symphony.security.utils.Converter;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

public class ClientCert {

  // Column:
  private byte[] clientCert;
  private byte[] wrappedRSAKey;
  // Row Key:
  private Long userId;
  private long certId;
  private int version;
  private String clientCertPEM;

  //used for caching
  private PublicKey publicKey;

  public ClientCert(Long userId, long certId, int version, byte[] clientCert, byte[] wrappedRSAKey) throws ClientCertException, UnsupportedEncodingException {

    if (clientCert == null)
      throw new ClientCertException("can not be null");
    // wrappedRSAKey may be null
    // userId may be null (as per SymphonyClient, it is optional)

    // Row Key:
    this.userId = userId;
    this.certId = certId;
    this.version = version;

    // Column:
    this.clientCert = clientCert;
    this.wrappedRSAKey = wrappedRSAKey;

    this.clientCertPEM = new String(clientCert, StandardCharsets.UTF_8);

    this.publicKey = null;
  }

  public byte[] getClientCert() {
    return clientCert;
  }

  public byte[] getWrappedRSAKey() {
    return wrappedRSAKey;
  }

  public long getCertId() {
    return certId;
  }

  public int getVersion() {
    return version;
  }

  public Long getUserId() {
    return userId;
  }

  public String getClientCertPEM() {
    return clientCertPEM;
  }

  public void setClientCertPEM(String clientCertPEM) {
    this.clientCertPEM = clientCertPEM;
    this.clientCert = clientCertPEM.getBytes(StandardCharsets.UTF_8);
  }

  public PublicKey getPublicKey() throws ClientCertException {
    if (publicKey == null) {
      try {
        if (clientCert != null) {
          X509Certificate certificate = Converter.getCertificateFromPem(clientCertPEM);
          publicKey = certificate.getPublicKey();
        }
      }
      catch(SymphonyPEMFormatException pemEx) {
        throw new ClientCertException("Unable to retrieve public key because failed to create certificate from PEM", pemEx);
      }
      //publicKey must contain a value by now
      if (publicKey == null) {
        StringBuilder errorMsg = new StringBuilder("Failed to retrieve public key from certificate for userId: ");
        errorMsg.append(userId);
        throw new ClientCertException(errorMsg.toString());
      }
    }
    return publicKey;
  }

  @JsonIgnore
  public String toString(){
    return "userId " + userId + ", certId " + certId + ", version " + version;
  }
}
