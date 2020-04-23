package com.symphony.security.exceptions;

/**
 * Created by sergey on 2/19/15.
 */
public class SymphonyEncryptionException extends Exception {

  private static final long serialVersionUID = -8934147119021184655L;

  public SymphonyEncryptionException() {
  }

  public SymphonyEncryptionException(String message) {
    super(message);
  }

  public SymphonyEncryptionException(String message, Throwable cause) {
    super(message, cause);
  }

  public SymphonyEncryptionException(String message, Throwable cause, boolean enableSuppression,
                                     boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  public SymphonyEncryptionException(Throwable cause) {
    super(cause);
  }
}
