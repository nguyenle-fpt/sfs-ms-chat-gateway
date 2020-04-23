package com.symphony.security.exceptions;

/**
 * Created by sergey on 7/8/16.
 */
public class KeyNotFoundException  extends KeyStoreException {
  public KeyNotFoundException(String s) {
    super(s);
  }
}
