package com.symphony.security.cache;

import com.amazonaws.util.Base64;

import java.util.HashMap;
import java.util.Map;

public class InMemoryPersister implements IPersister {

  Map<String, byte[]> persister;

  public InMemoryPersister() {
    persister = new HashMap<>();
  }

  @Override
  public void close() {
    persister = new HashMap<>();
  }

  @Override
  public void persist(byte[] bufId, byte[] in) {
    persister.put(Base64.encodeAsString(bufId), in);
  }

  @Override
  public byte[] retrieve(byte[] bufId) {
    return persister.get(Base64.encodeAsString(bufId));
  }

  @Override
  public void delete(byte[] bufId) {
    persister.remove(Base64.encodeAsString(bufId));
  }

  @Override
  public String getType() {
    return "InMemoryPersister(of size " + persister.size() + ")";
  }
}
