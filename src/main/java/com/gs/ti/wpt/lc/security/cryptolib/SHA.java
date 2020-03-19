package com.gs.ti.wpt.lc.security.cryptolib;

import com.symphony.security.exceptions.SymphonyEncryptionException;
import com.symphony.security.exceptions.SymphonyInputException;

public final class SHA {

    static {
        Utils.loadCryptoLib();
    }

    // No one should ever create an instance of this class, so
    // the constructor is private.
    private SHA() { }

    // The native implemenation.
    private static native int nativeSHA2x256(byte[] In, byte[] Out);
    
    // JNI wrapper glue.
    public static byte[] SHA2x256(byte[] In) throws SymphonyInputException,
        SymphonyEncryptionException {
        if (In == null)
            throw new SymphonyInputException("Invalid parameter(s) - can not pass null's");
        byte[] Out = new byte[32];
        int retVal = nativeSHA2x256(In, Out);
        if (retVal != 0)
            throw new SymphonyEncryptionException("nativeSHA2x256 failed. " + Out);
        return Out;
    }
}
