package com.symphony.security.cache;

import com.symphony.security.exceptions.SymphonyEncryptionException;
import com.symphony.security.exceptions.SymphonyInputException;
import com.symphony.security.exceptions.SymphonyNativeException;

public interface IPersister {
  void close() throws SymphonyInputException;

  void persist(byte[] bufId, byte[] in) throws SymphonyEncryptionException, SymphonyInputException,
      SymphonyNativeException;

  byte[] retrieve(byte[] bufId) throws SymphonyEncryptionException, SymphonyInputException;

  void delete(byte[] bufId) throws SymphonyInputException, SymphonyNativeException;

  String getType();
}
