package com.gs.ti.wpt.lc.security.cryptolib;

import com.symphony.security.exceptions.SymphonyInputException;


public final class Crl
{
    static
    {
        Utils.loadCryptoLib();
    }

    private Crl() {}

    //Signature Algorithms short/long names allowed for CRLs
    private static final String[] SIG_ALG = {"sha256", "sha384", "sha512"};

    //Native code
    private static native long nativeInitCrl(String Cert, String[] Ext, int LastUpdate, int NextUpdate);
    private static native long nativeFillCrl(long Crl, RevokedCert Rvk);
    private static native long nativeSignCrl(long Crl, long RSAKeyPair, String SigAlg);

    private static native void nativeFreeCrl(long Crl);
    private static native String nativeSerializePEMCrl(long Crl);

    //Public functions
    public static String createCRL(String PEMEncodedRSAKeyPair, String Cert, int LastUpdate, int NextUpdate) throws Exception
    {
        return createCRL(PEMEncodedRSAKeyPair, Cert, null, LastUpdate, NextUpdate, null, null);
    }
    public static String createCRL(String PEMEncodedRSAKeyPair, String Cert, String SigAlg, int LastUpdate, int NextUpdate) throws Exception
    {
        return createCRL(PEMEncodedRSAKeyPair, Cert, SigAlg, LastUpdate, NextUpdate, null, null);
    }
    public static String createCRL(String PEMEncodedRSAKeyPair, String Cert, int LastUpdate, int NextUpdate, Extensions Ext) throws Exception
    {
        return createCRL(PEMEncodedRSAKeyPair, Cert, null, LastUpdate, NextUpdate, Ext, null);
    }
    public static String createCRL(String PEMEncodedRSAKeyPair, String Cert, int LastUpdate, int NextUpdate, RevokedCert[] Rvk) throws Exception
    {
        return createCRL(PEMEncodedRSAKeyPair, Cert, null, LastUpdate, NextUpdate, null, Rvk);
    }
    public static String createCRL(String PEMEncodedRSAKeyPair, String Cert, String SigAlg, int LastUpdate, int NextUpdate, Extensions Ext) throws Exception
    {
        return createCRL(PEMEncodedRSAKeyPair, Cert, SigAlg, LastUpdate, NextUpdate, Ext, null);
    }
    public static String createCRL(String PEMEncodedRSAKeyPair, String Cert, String SigAlg, int LastUpdate, int NextUpdate, RevokedCert[] Rvk) throws Exception
    {
        return createCRL(PEMEncodedRSAKeyPair, Cert, SigAlg, LastUpdate, NextUpdate, null, Rvk);
    }
    public static String createCRL(String PEMEncodedRSAKeyPair, String Cert, int LastUpdate, int NextUpdate, Extensions Ext, RevokedCert[] Rvk) throws Exception
    {
        return createCRL(PEMEncodedRSAKeyPair, Cert, null, LastUpdate, NextUpdate, Ext, Rvk);
    }

    public static String createCRL(String PEMEncodedRSAKeyPair, String Cert, String SigAlg, int LastUpdate, int NextUpdate, Extensions Ext, RevokedCert[] Rvk) throws Exception
    {
        //Check validity of inputs

        if (PEMEncodedRSAKeyPair == null || PEMEncodedRSAKeyPair.length() == 0 || Cert == null || Cert.length() == 0 || LastUpdate < 0 || NextUpdate < 0)
            throw new Exception("You entered invalid input. You can not pass null's to:\n-key\n-name\n-revoked certificates\nYou can not pass a negative to Last/Next Update");

        long RSAKeyPairPtr = RSA.mallocRSAKeyPairFromPem(PEMEncodedRSAKeyPair);

        if (RSAKeyPairPtr == 0)
            throw new Exception("Failed to nativeDeserializaRSAPubKey.");

        String[] extArray = null;
        if (Ext != null && Ext.getExtensions().size() > 0)
        {
            for (int i = 0; i < Ext.getCrtExt().length; i++)
            {
                if (Ext.getExtensions().containsKey((Ext.getCrtExt())[i]))
                {
                    RSA.freeRSAKeyPair(RSAKeyPairPtr);
                    throw new Exception("You passed an invalid extension to the CRL: " + Ext.getCrtExt()[i]);
                }
            }

            extArray = Ext.createArrayExt();
            if (extArray == null)
            {
                RSA.freeRSAKeyPair(RSAKeyPairPtr);
                throw new Exception("There was a problem creating the extensions array.");
            }
        }

        if (SigAlg != null && SigAlg.length() > 0)
        {
            int i = 0;

            for (; i < SIG_ALG.length; i++)
                if (SIG_ALG[i] == SigAlg)
                    break;

            if (i == SIG_ALG.length)
            {
                RSA.freeRSAKeyPair(RSAKeyPairPtr);
                throw new SymphonyInputException("Unsupported signature algorithm.");
            }
        }

        //Initialize CRL
        long crl = nativeInitCrl(Cert, extArray, LastUpdate, NextUpdate);
        if (crl == 0)
        {
            RSA.freeRSAKeyPair(RSAKeyPairPtr);
            throw new Exception("Failed to create CRL via native code (initialization).");
        }

        //Fill CRL with RevokedCert certs
        if (Rvk != null)
        {
            for (int i = 0; i < Rvk.length; i++)
            {
                String[] tmp = Rvk[i].getExt();
                for (int l = 0; l < tmp.length; l++)
                {
                    for (int j = 0; j < Ext.getCrtExt().length; j++)
                    {
                        if (tmp[l].contains(Ext.getCrtExt()[j]))
                        {
                            RSA.freeRSAKeyPair(RSAKeyPairPtr);
                            throw new Exception("Failed to create CRL. Illegal entry extension: " + tmp[l]);
                        }
                    }
                }

                if (Rvk[i] != null && Rvk[i].getSerial() != null && Rvk[i].getSerial().length() > 0)
                {
                    crl = nativeFillCrl(crl, Rvk[i]);
                    if (crl == 0)
                    {
                        RSA.freeRSAKeyPair(RSAKeyPairPtr);
                        throw new Exception("Failed to create CRL via native code (adding revoked).");
                    }
                }
            }
        }
        Ext = null;
        Rvk = null;

        //Sign CRL
        crl = nativeSignCrl(crl, RSAKeyPairPtr, SigAlg);

        if (crl == 0)
        {
            RSA.freeRSAKeyPair(RSAKeyPairPtr);
            throw new Exception("Failed to create CRL via native code (signing).");
        }

        //Finish
        String PEMEncodedCRL = nativeSerializePEMCrl(crl);
        nativeFreeCrl(crl);

        if (PEMEncodedCRL == null)
        {
            RSA.freeRSAKeyPair(RSAKeyPairPtr);
            throw new Exception("Failed to nativeSerializePEMCrl");
        }

        RSA.freeRSAKeyPair(RSAKeyPairPtr);

        return PEMEncodedCRL;
    }
}
