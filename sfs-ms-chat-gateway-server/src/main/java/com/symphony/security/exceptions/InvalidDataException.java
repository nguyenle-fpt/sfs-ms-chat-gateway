package com.symphony.security.exceptions;

/**
 * </p>Created by Ivan Rylach on 7/16/15
 *
 * @author i@symphony.com
 */
public class InvalidDataException extends Exception {

  public InvalidDataException() {
    super();
  }

  public InvalidDataException(String message) {
    super(message);
  }

  public InvalidDataException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidDataException(Throwable cause) {
    super(cause);
  }

  protected InvalidDataException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
