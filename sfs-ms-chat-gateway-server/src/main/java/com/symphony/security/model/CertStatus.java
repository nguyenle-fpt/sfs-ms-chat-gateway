package com.symphony.security.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigInteger;
import java.util.Date;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CertStatus {

  private BigInteger serialNumber;
  private long revocationDate;
  private int reason;
  private long invalidityDate;

  public CertStatus(BigInteger serialNumber, long revocationDate, int reason, long invalidityDate) {
    this.serialNumber = serialNumber;
    this.revocationDate = revocationDate;
    this.reason = reason;
    this.invalidityDate = invalidityDate;
  }

  public BigInteger getSerialNumber() {
    return serialNumber;
  }

  public long getDate() {
    return revocationDate;
  }

  public int getReason() {
    return reason;
  }

  public long getInvalidity() {
    return invalidityDate;
  }

  @JsonIgnore
  public String toString(){
    return "certificateSerialNumber: 0x" + serialNumber.toString(16) +
        ",  revocationDate: " + (new Date(revocationDate)).toString() +
        ",  revocationReason: " + reason +
        ",  invalidityDate: " + ((invalidityDate == 0)? "0": (new Date(invalidityDate)).toString());
  }
}
