package com.symphony.security.exceptions;

public class SymphonyInputException extends Exception {

  private static final long serialVersionUID = -8934147119021184655L;

  public SymphonyInputException() {
  }

  public SymphonyInputException(String message) {
    super(message);
  }

  public SymphonyInputException(String message, Throwable cause) {
    super(message, cause);
  }

  public SymphonyInputException(String message, Throwable cause, boolean enableSuppression,
                                     boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  public SymphonyInputException(Throwable cause) {
    super(cause);
  }
}
