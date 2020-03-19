package com.symphony.security.exceptions;

/**
 * Created by sergey on 2/19/15.
 */
public class SymphonyPEMFormatException extends Exception {

  private static final long serialVersionUID = -8934147119021184655L;

  public SymphonyPEMFormatException() {
    super("PEM string IOException");
  }

  public SymphonyPEMFormatException(String message) {
    super(message);
  }

  public SymphonyPEMFormatException(String message, Throwable cause) {
    super(message, cause);
  }

  public SymphonyPEMFormatException(String message, Throwable cause, boolean enableSuppression,
                                    boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  public SymphonyPEMFormatException(Throwable cause) {
    super(cause);
  }
}
