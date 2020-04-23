package com.symphony.security.exceptions;


import org.apache.commons.codec.binary.Base64;

public class SymphonyComputedKeyIdAndExpectedKeyIdMistmatchException extends SymphonyEncryptionException {

  public SymphonyComputedKeyIdAndExpectedKeyIdMistmatchException(byte[] theirs, byte[] mine) {
    super( "Theirs: [" + Base64.encodeBase64String(theirs) + "] Mine: [" + Base64.encodeBase64String(mine) + "]" );
  }

}
