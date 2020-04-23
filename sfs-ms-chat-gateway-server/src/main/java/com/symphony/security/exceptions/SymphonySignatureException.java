package com.symphony.security.exceptions;

/**
 * Created by sergey on 2/19/15.
 */
public class SymphonySignatureException extends Exception {

  private static final long serialVersionUID = -8934147119021184655L;

  public SymphonySignatureException() {
  }

  public SymphonySignatureException(String message) {
    super(message);
  }

  public SymphonySignatureException(String message, Throwable cause) {
    super(message, cause);
  }

  public SymphonySignatureException(String message, Throwable cause, boolean enableSuppression,
                                    boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  public SymphonySignatureException(Throwable cause) {
    super(cause);
  }
}
