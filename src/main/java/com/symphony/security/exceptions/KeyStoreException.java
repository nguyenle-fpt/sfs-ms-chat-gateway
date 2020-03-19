package com.symphony.security.exceptions;

public class KeyStoreException extends Exception {
  public KeyStoreException(String s) {
    super(s);
  }
  public KeyStoreException(String s, Throwable t) { super(s,t); }
  public KeyStoreException(Throwable t) { super(t); }
}
