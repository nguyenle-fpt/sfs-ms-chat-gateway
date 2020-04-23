package com.symphony.security.exceptions;

public class ClientCertException extends KeyStoreException {
    public ClientCertException(String s) {
      super(s);
    }
    public ClientCertException(String s, Throwable t) {
      super(s, t);
    }
}
