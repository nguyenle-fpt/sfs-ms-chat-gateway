package com.symphony.security.model;


public class ClientCertInfo {

  final private ClientCert clientCert;
  final private CertStatus certStatus;
  final private long notBefore;
  final private long notAfter;

  public ClientCertInfo(ClientCert clientCert, CertStatus certStatus, long notBefore, long notAfter) {
    this.clientCert = clientCert;
    this.certStatus = certStatus;
    this.notBefore = notBefore;
    this.notAfter = notAfter;
  }

  public ClientCert getClientCert() {
    return clientCert;
  }

  public CertStatus getCertStatus() {
    return certStatus;
  }

  public long getNotBefore() {
    return notBefore;
  }

  public long getNotAfter() {
    return notAfter;
  }
}
