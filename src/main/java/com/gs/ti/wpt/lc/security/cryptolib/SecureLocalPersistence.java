package com.gs.ti.wpt.lc.security.cryptolib;

import com.symphony.security.cache.IPersister;
import com.symphony.security.exceptions.SymphonyEncryptionException;
import com.symphony.security.exceptions.SymphonyInputException;
import com.symphony.security.exceptions.SymphonyNativeException;

import java.nio.ByteBuffer;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SecureLocalPersistence implements IPersister {

  static {
    Utils.loadCryptoLib();
  }

  long secureLocalPersistenceHandle;

  public SecureLocalPersistence(byte[] seed, String path) throws SymphonyInputException,
      SymphonyNativeException {
    init(seed, path);
  }

  // native code.
  private static native long nativeInitSecurePersistence(byte[] Seed, String Path);

  private static native int nativeSecurePersist(long secureLocalPersistenceHandle, int BufId, byte[] Buf);

  private static native byte[] nativeSecureRetrieve(long secureLocalPersistenceHandle, int BufId);

  private static native int nativeSecureDelete(long secureLocalPersistenceHandle, int BufId);

  private static native void nativeSecureFree(long secureLocalPersistenceHandle);

  // Wrapper glue.
  private void verifyInited() throws SymphonyInputException {
    if (secureLocalPersistenceHandle == 0)
      throw new SymphonyInputException("Failed to init.");
  }

  private void init(byte[] seed, String path) throws SymphonyInputException, SymphonyNativeException {
    if (seed == null || path == null)
      throw new SymphonyInputException("secureDir path can not be null, validate secureDir param in configuration");
    Path secureDirPath = FileSystems.getDefault().getPath(path);
    if (Files.notExists(secureDirPath)) {
      throw new SymphonyInputException("secureDirPath does not exist, validate secureDir param in configuration");
    }
    if (secureLocalPersistenceHandle != 0) {
      throw new SymphonyInputException("The instance has already been initialized. You may only init once.");
    }
    this.secureLocalPersistenceHandle = nativeInitSecurePersistence(seed, path);
    if (secureLocalPersistenceHandle == 0) {
      throw new SymphonyInputException("Failed to construct a new secureLocalPersistenceHandle");
    }
  }

  public void close() throws SymphonyInputException {
    verifyInited();
    nativeSecureFree(secureLocalPersistenceHandle);
    secureLocalPersistenceHandle = 0;
  }

  private int getBufId(byte[] bufId) throws SymphonyInputException {
    try {
      return ByteBuffer.wrap(SHA.SHA2x256(bufId)).getInt();
    } catch (SymphonyEncryptionException e) {
      throw new SymphonyInputException("SHA256 failed", e);
    }
  }

  @Override
  public void persist(byte[] bufId, byte[] in) throws SymphonyInputException, SymphonyNativeException {
    if (bufId == null)
      throw new SymphonyInputException("bufId can not be null");

    persist(getBufId(bufId), in);
  }

  @Override
  public byte[] retrieve(byte[] bufId) {
    try {
      return retrieve(getBufId(bufId));
    } catch (SymphonyInputException | SymphonyNativeException e) {
      // for non existent bufId, and failed-to-init, and already closed, persisters
      return null;
    }
  }

  @Override
  public void delete(byte[] bufId) throws SymphonyInputException, SymphonyNativeException {
    delete(getBufId(bufId));
  }

  @Override
  public String getType() {
        return "SecureLocalPersister(handle = " + secureLocalPersistenceHandle + ")";
  }
  public void persist(int bufId, byte[] in) throws SymphonyInputException, SymphonyNativeException {
    verifyInited();
    if (in == null)
      throw new SymphonyInputException("can not pass null's");

    int ret = nativeSecurePersist(secureLocalPersistenceHandle, bufId, in);
    if (ret < 0) {
      throw new SymphonyNativeException(
          "nativeSecurePersist returned an error: " + Integer.toString((ret)));
    }
  }

  public byte[] retrieve(int bufId) throws SymphonyInputException, SymphonyNativeException {
    verifyInited();
    byte[] ret = nativeSecureRetrieve(secureLocalPersistenceHandle, bufId);
    if (ret == null) {
      throw new SymphonyNativeException("nativeSecureRetrieve returned an error. (are you sure the BufID's file exists?)");
    }
    return ret;
  }

  public void delete(int bufId) throws SymphonyInputException, SymphonyNativeException {
    verifyInited();
    int ret = nativeSecureDelete(secureLocalPersistenceHandle, bufId);
    if (ret != 0) {
      throw new SymphonyNativeException(" returned an error: " + Integer.toString(ret));
    }
  }

}
