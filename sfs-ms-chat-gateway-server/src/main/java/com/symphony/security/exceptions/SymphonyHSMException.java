package com.symphony.security.exceptions;

public class SymphonyHSMException extends Exception {

  private static final long serialVersionUID = -8934147119021184655L;

  public SymphonyHSMException() {
  }

  public SymphonyHSMException(String message) {
    super(message);
  }

  public SymphonyHSMException(String message, Throwable cause) {
    super(message, cause);
  }

  public SymphonyHSMException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  public SymphonyHSMException(Throwable cause) {
    super(cause);
  }
}
