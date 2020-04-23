package com.symphony.security.exceptions;

public class SymphonyNativeException extends Exception {

  private static final long serialVersionUID = -8934147119021184655L;

  public SymphonyNativeException() {
  }

  public SymphonyNativeException(String message) {
    super(message);
  }

  public SymphonyNativeException(String message, Throwable cause) {
    super(message, cause);
  }

  public SymphonyNativeException(String message, Throwable cause, boolean enableSuppression,
                                boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  public SymphonyNativeException(Throwable cause) {
    super(cause);
  }
}
