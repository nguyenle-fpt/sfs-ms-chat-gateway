package com.symphony.security.hsm;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.util.Arrays;

import java.io.IOException;

/**
 * Created by Ivan Rylach on 5/26/15.
 *
 * @author i@symphony.com
 */
@Deprecated
public class TransportableWrappedKey {

  public static final int EXPECTED_MAX_LENGTH_OF_TRANSPORTABLE_BYTES = 1712;
  private static final int IV_LEN = 16;

  private byte[] transportableBytes;
  private byte[] wrappedKeyBytes;
  private byte[] iv;

  public TransportableWrappedKey(byte[] wrappedKeyBytes, byte[] iv) throws IOException {
    this.wrappedKeyBytes = wrappedKeyBytes;
    this.iv = iv;
    this.transportableBytes = Arrays.concatenate(iv, wrappedKeyBytes);
    validateInput(this.transportableBytes);
  }

  public TransportableWrappedKey(byte[] transportableBytes) throws IOException {
    validateInput(transportableBytes);
    this.transportableBytes = transportableBytes;
    this.iv = Arrays.copyOfRange(transportableBytes, 0, IV_LEN);
    this.wrappedKeyBytes = Arrays.copyOfRange(transportableBytes, IV_LEN, transportableBytes.length);
  }

  private void validateInput(byte[] transportableBytes) throws IOException {
    if(transportableBytes == null){
      throw new IOException("transportableBytes is null, it must have at least " + IV_LEN);
    }
    if (transportableBytes.length < IV_LEN) {
      throw new IOException("Note enough transportable bytes (" + transportableBytes.length + "), must have at least " + IV_LEN);
    }
  }

  public byte[] getWrappedKeyBytes() {
    return wrappedKeyBytes;
  }

  public byte[] getIv() {
    return iv;
  }

  public byte[] getTransportableBytes() {
    return transportableBytes;
  }

  @Override
  public String toString() {
    return Base64.encodeBase64String(transportableBytes);
  }
}
