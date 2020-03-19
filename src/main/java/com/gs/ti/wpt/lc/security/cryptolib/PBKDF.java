package com.gs.ti.wpt.lc.security.cryptolib;

import com.symphony.security.exceptions.SymphonyEncryptionException;
import com.symphony.security.exceptions.SymphonyInputException;

public final class PBKDF {

    static {
        Utils.loadCryptoLib();
    }

    private PBKDF() {}

    private static native int nativePBKDF2(byte[] Password, byte[] Salt, int Iters, byte[] Out);
    
    // Returns a 32 byte key, given a Password, Salt, and number of iterations.
    public static byte[] PBKDF2_SHA256(byte[] Password, byte Salt[], int Iters) throws SymphonyInputException, SymphonyEncryptionException {
        if (Password == null || Salt == null || Iters == 0)
            throw new SymphonyInputException("Invalid parameter(s) - can not pass null's / zero iter");

        byte[] Out = new byte[32];
        int retVal = nativePBKDF2(Password, Salt, Iters, Out);
        if (retVal != 0)
            throw new SymphonyEncryptionException("nativePBKDF2 failed with the ret val " + retVal);
        return Out;
    }
}
