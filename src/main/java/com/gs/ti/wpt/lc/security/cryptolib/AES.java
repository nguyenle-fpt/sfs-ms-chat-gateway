package com.gs.ti.wpt.lc.security.cryptolib;

import com.symphony.security.exceptions.SymphonyEncryptionException;
import com.symphony.security.exceptions.SymphonyInputException;

public final class AES {

  static {
    Utils.loadCryptoLib();
  }

  // No one should ever create an instance of this class, so
  // the constructor is private.
  private AES() {
  }

  // native code.
  private static native int nativeDecryptGCM(byte[] In, byte[] AAD, byte[] Tag, byte[] Key, byte[] IV, byte[] Out);

  private static native int nativeEncryptGCM(byte[] In, byte[] AAD, byte[] Key, byte[] IV, byte[] Out, byte[] Tag);

  private static native int nativeDecryptCBC(byte[] In, byte[] Key, byte[] IV, byte[] Out);

  private static native int nativeEncryptCBC(byte[] In, byte[] Key, byte[] IV, byte[] Out);

  // Wrapper glue.
  public static byte[] DecryptGCM(byte[] In, byte[] AAD, byte[] Key, byte[] IV, byte[] Tag) throws
      SymphonyInputException, SymphonyEncryptionException {

    if (In == null || AAD == null || Key == null || IV == null || Tag == null)
      throw new SymphonyInputException("can not pass null's");

    if (Key.length % 16 != 0)
      throw new SymphonyInputException("Invalid Key length of " + Key.length + " detected");

    if (IV.length != 16)
      throw new SymphonyInputException("Invalid IV length of " + IV.length + " detected");

    byte[] Out = new byte[In.length];
    int ret = nativeDecryptGCM(In, AAD, Tag, Key, IV, Out);
    if (ret < 0) {
      throw new SymphonyEncryptionException(
          "nativeDecryptGCM returned an error: " + Integer.toString((ret)));
    }
    return Out;
  }

  public static byte[] EncryptGCM(byte[] In, byte[] AAD, byte[] Key, byte[] IV, byte[] Tag) throws SymphonyInputException, SymphonyEncryptionException {
    if (In == null || AAD == null || Key == null || IV == null || Tag == null)
      throw new SymphonyInputException("can not pass null's");

    if (Key.length % 16 != 0)
      throw new SymphonyInputException("Invalid Key length of " + Key.length + " detected");

    if (IV.length != 16)
      throw new SymphonyInputException("Invalid IV length of " + IV.length + " detected");

    byte[] Out = new byte[In.length];
    int ret = nativeEncryptGCM(In, AAD, Key, IV, Out, Tag);
    if (ret < 0) {
      throw new SymphonyEncryptionException(
          "nativeEncryptGCM returned an error: " + Integer.toString((ret)));
    }
    return Out;
  }


  public static byte[] DecryptCBC(byte[] In, byte[] Key, byte[] IV) throws SymphonyInputException, SymphonyEncryptionException {
    if (In == null || Key == null || IV == null)
      throw new SymphonyInputException("can not pass null's");

    if (In.length % 16 != 0)
      throw new SymphonyInputException("Invalid In length of " + In.length + " detected");

    if (Key.length % 16 != 0)
      throw new SymphonyInputException("Invalid Key length of " + Key.length + " detected");

    if (IV.length != 16)
      throw new SymphonyInputException("Invalid IV length of " + IV.length + " detected");

    byte[] Out = new byte[In.length];
    int ret = nativeDecryptCBC(In, Key, IV, Out);
    if (ret < 0) {
      throw new SymphonyEncryptionException(
          "nativeDecryptGCM returned an error: " + Integer.toString((ret)));
    }
    return Out;
  }

  public static byte[] EncryptCBC(byte[] In, byte[] Key, byte[] IV) throws SymphonyInputException, SymphonyEncryptionException {
    if (In == null || Key == null || IV == null)
      throw new SymphonyInputException("can not pass null's");

    if (In.length % 16 != 0)
      throw new SymphonyInputException("Invalid In length of " + In.length + " detected");

    if (Key.length % 16 != 0)
      throw new SymphonyInputException("Invalid Key length of " + Key.length + " detected");

    if (IV.length != 16)
      throw new SymphonyInputException("Invalid IV length of " + IV.length + " detected");

    byte[] Out = new byte[In.length];
    int ret = nativeEncryptCBC(In, Key, IV, Out);
    if (ret < 0) {
      throw new SymphonyEncryptionException(
          "nativeEncryptGCM returned an error: " + Integer.toString((ret)));
    }
    return Out;
  }
}
