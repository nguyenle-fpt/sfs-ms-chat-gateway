package com.gs.ti.wpt.lc.security.cryptolib;

import com.symphony.security.exceptions.SymphonyPEMFormatException;
import com.symphony.security.exceptions.SymphonyInputException;
import com.symphony.security.exceptions.SymphonySignatureException;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import javax.naming.ldap.LdapName;


public final class Cert {

	static {
		Utils.loadCryptoLib();
	}

	// No one should ever create an instance of this class, so
	// the constructor is private.
	private Cert() { }

	// native code.
	private static native String nativeSerializePEMCert(long Cert);
	private static native long nativeDeserializePEMCert(String Cert);
	private static native String nativeSerializePEMCertSignReq(long CSR);
	private static native long nativeDeserializePEMCertSignReq(String CSR);
	private static native long nativeCreateSelfSignedCert(long RSAKeyPair, String SerialNum, String CountryCodeStr, String OrgStr, String CommonNameStr, String Uid, int ValidStartInXDays, int ValidForXDays);
  private static native long nativeSignCSR(long CSR, long SigningRSA, long SigningCert, String SerialNum, String sigAlg, String[] extArray, int ValidStartInXDays, int ValidForXDays);
	private static native long nativeCreateCertSignReq(long Cert, long KeyPair);
	private static native long nativeGetPubKey(long Cert);
	private static native long nativeGetReqPubKey(long Req);
	private static native void nativeFreeCert(long Cert);
	private static native void nativeFreeCertSignReq(long Cert);

	private static native long nativeCreateSelfSignedCertificate(long RSAKeyPairPtr, String SerialNumber, String sig, String[] rdns, String[] ext, int ValidStartingInXDays, int ValidForXDays);
	private static native long nativeCreateCertSignRequest(String sigalg, String[] rdns, long SigningRSA);

	//Signature Algorithm array
	private static final String[] SIG_ALG = {"sha256", "sha384", "sha512"};
	
	// Wrapper glue.
	public static String createSelfSigned(String PEMEncodedRSAKeyPair, int SerialNumber, String CoutrnyCode, String Org, String CommonName, int ValidStartingInXDays, int ValidForXDays) throws SymphonyInputException,
      SymphonyPEMFormatException, SymphonySignatureException, UnsupportedEncodingException {
		return createSelfSigned(PEMEncodedRSAKeyPair, BigInteger.valueOf((long)SerialNumber), CoutrnyCode, Org, CommonName, null, ValidStartingInXDays, ValidForXDays);
	}

  public static String createSelfSigned(String PEMEncodedRSAKeyPair, BigInteger SerialNumber, String CoutrnyCode, String Org, String CommonName, String Uid, int ValidStartingInXDays, int ValidForXDays) throws SymphonyInputException, SymphonyPEMFormatException, SymphonySignatureException, UnsupportedEncodingException {
    if (PEMEncodedRSAKeyPair == null || CoutrnyCode == null || Org == null || ((CommonName == null || CommonName.isEmpty()) && (Uid == null || Uid.isEmpty())) ||
      ValidForXDays < 1 || SerialNumber == null || SerialNumber.compareTo(BigInteger.ONE) < 0)
      throw new SymphonyInputException("can not pass null's, and ValidForXDays must be at least 1 and SerialNumber must be at least 1");

    if (CommonName == null)
      CommonName = "";
    if (Uid == null)
      Uid = "";
    long RSAKeyPairPtr = RSA.mallocRSAKeyPairFromPem(PEMEncodedRSAKeyPair);
    if (RSAKeyPairPtr == 0) {
      throw new SymphonyPEMFormatException("Failed to nativeDeserializeRSAPubKey");
    }

    if (ValidForXDays == 0) {
      throw new SymphonyInputException("Your cert needs to be valid for atleast one day.");
    }

    long cert = nativeCreateSelfSignedCert(RSAKeyPairPtr, SerialNumber.toString(), CoutrnyCode, Org, CommonName, Uid, ValidStartingInXDays, ValidForXDays);
    if (cert == 0) {
      throw new SymphonySignatureException("Failed to nativeCreateSelSignedCert");
    }

    String PEMEncodedCert = nativeSerializePEMCert(cert);
    if (PEMEncodedCert == null) {
      throw new SymphonyPEMFormatException("Failed to nativeSerializePEMCert");
    }

    nativeFreeCert(cert);
    RSA.freeRSAKeyPair(RSAKeyPairPtr);

    return PEMEncodedCert;
  }

  public static String getReqPubKey(String REQStr) throws SymphonyInputException, SymphonyPEMFormatException, SymphonySignatureException, UnsupportedEncodingException {
		if (REQStr == null)
			throw new SymphonyInputException("can not pass null's, and ValidForXDays must be at least 1 and SerialNumber must be at least 1");
	
		long req = nativeDeserializePEMCertSignReq(REQStr);
		if (req == 0) {
			throw new SymphonyPEMFormatException("Failed to nativeDeserializePEMCertSignReq");
		}

		long RSAPubKey = nativeGetReqPubKey(req);

		if (RSAPubKey == 0) {
			nativeFreeCertSignReq(req);
			throw new SymphonySignatureException("Failed to nativeGetReqPubKey");
		}

		String RSAPubKeyPEM = RSA.serializeRSAPubKey(RSAPubKey);

		nativeFreeCertSignReq(req);

		if (RSAPubKeyPEM.length() == 0) {
			throw new SymphonyPEMFormatException("nativeSerializeRSAPubKey failed.");
		}

		// DO NOT DO THIS.  It's a double free on the cert.
		// RSA.freeRSAKeyPair(RSAPubKey);

		return RSAPubKeyPEM;
	}

	public static String getPubRSA(String CertStr) throws SymphonyInputException, SymphonyPEMFormatException, SymphonySignatureException, UnsupportedEncodingException {
		if (CertStr == null)
			throw new SymphonyInputException("can not pass null's, and ValidForXDays must be at least 1 and SerialNumber must be at least 1");
		Utils.validateCString(CertStr);

		long cert = nativeDeserializePEMCert(CertStr);
		if (cert == 0) {
			throw new SymphonyPEMFormatException("Failed to nativeDeserializePEMCert");
		}

		long RSAPubKey = nativeGetPubKey(cert);
		if (RSAPubKey == 0) {
			throw new SymphonySignatureException("Failed to nativeGetPubKey");
		}

		String RSAPubKeyPEM = RSA.serializeRSAPubKey(RSAPubKey);
		nativeFreeCert(cert);
		if (RSAPubKeyPEM.length() == 0) {
			throw new SymphonyPEMFormatException("nativeSerializeRSAPubKey failed.");
		}

		// DO NOT DO THIS.  It's a double free on the cert.
		// RSA.freeRSAKeyPair(RSAPubKey);

		return RSAPubKeyPEM;
	}

	public static String createCSR(String CertPEM, String SigningRSAKeyPairPEM) throws SymphonyInputException, SymphonyPEMFormatException, SymphonySignatureException, UnsupportedEncodingException {
		if (CertPEM == null || SigningRSAKeyPairPEM == null)
			throw new SymphonyInputException("can not pass null's");
		Utils.validateCString(CertPEM);
		Utils.validateCString(SigningRSAKeyPairPEM);

		long Cert = nativeDeserializePEMCert(CertPEM);
		if (Cert == 0) {
			throw new SymphonyPEMFormatException("Failed to nativeDeserializePEMCert");
		}

		long SigningRSA = RSA.mallocRSAKeyPairFromPem(SigningRSAKeyPairPEM);
		if (SigningRSA == 0) {
			nativeFreeCert(Cert);
			throw new SymphonyPEMFormatException("Failed to mallocRSAKeyPairFromPem");
		}

		long CSR = nativeCreateCertSignReq(Cert, SigningRSA);
		if (CSR == 0) {
			nativeFreeCert(Cert);
			RSA.freeRSAKeyPair(SigningRSA);
			throw new SymphonyPEMFormatException("Failed to nativeCreateCertSignReq");
		}

		String CSRPEM = nativeSerializePEMCertSignReq(CSR);

		nativeFreeCertSignReq(CSR);
		RSA.freeRSAKeyPair(SigningRSA);
		nativeFreeCert(Cert);

		return CSRPEM;
	}

  public static String signCSR(String CSRPEM, String SigningRSAKeyPairPEM, String SigningCertPEM, int SerialNum, int ValidStartingInXDays, int ValidForXDays) throws SymphonyInputException, SymphonyPEMFormatException, SymphonySignatureException, UnsupportedEncodingException {
    return signCSR(CSRPEM, SigningRSAKeyPairPEM, SigningCertPEM, BigInteger.valueOf((long)SerialNum), ValidStartingInXDays, ValidForXDays);
  }

  public static String signCSR(String CSRPEM, String SigningRSAKeyPairPEM, String SigningCertPEM, BigInteger SerialNum, int ValidStartingInXDays, int ValidForXDays) throws SymphonyInputException, SymphonyPEMFormatException, SymphonySignatureException, UnsupportedEncodingException {
    return signCSR(CSRPEM, SigningRSAKeyPairPEM, SigningCertPEM, SerialNum, null, null, ValidStartingInXDays, ValidForXDays);
  }

  public static String signCSR(String CSRPEM, String SigningRSAKeyPairPEM, String SigningCertPEM, BigInteger SerialNum, String sigAlg, Extensions exts, int ValidStartingInXDays, int ValidForXDays) throws SymphonyInputException, SymphonyPEMFormatException, SymphonySignatureException, UnsupportedEncodingException {
    if (CSRPEM == null || SigningRSAKeyPairPEM == null || SigningCertPEM == null || ValidForXDays < 1 || SerialNum == null || SerialNum.compareTo(BigInteger.ONE) < 0)
      throw new SymphonyInputException("can not pass null's, and ValidForXDays must be at least 1, and SerialNum must be at least 1");
    Utils.validateCString(CSRPEM);
    Utils.validateCString(SigningRSAKeyPairPEM);
    Utils.validateCString(SigningCertPEM);

    long CSR = nativeDeserializePEMCertSignReq(CSRPEM);
    if (CSR == 0) {
      throw new SymphonyPEMFormatException("Failed to nativeDeserializePEMCertSignReq: " + CSRPEM);
    }

    long SigningCert = nativeDeserializePEMCert(SigningCertPEM);
    if (SigningCert == 0) {
      nativeFreeCertSignReq(CSR);
      throw new SymphonyPEMFormatException("Failed to nativeDeserializePEMCert");
    }

    long SigningRSA = RSA.mallocRSAKeyPairFromPem(SigningRSAKeyPairPEM);
    if (SigningRSA == 0) {
      nativeFreeCertSignReq(CSR);
      nativeFreeCert(SigningCert);
      throw new SymphonyPEMFormatException("Failed to mallocRSAKeyPairFromPem");
    }

    String[] extArray = null;
    if (exts != null && exts.getExtensions().size() > 0) {
      extArray = exts.createArrayExt();
      if (extArray == null) {
        nativeFreeCertSignReq(CSR);
        nativeFreeCert(SigningCert);
        RSA.freeRSAKeyPair(SigningRSA);
        throw new SymphonyInputException("Invalid extensions.");
      }
    }

    if (sigAlg != null) {
      int i = 0;
      for (; i < SIG_ALG.length; i++) {
        if (SIG_ALG[i].equalsIgnoreCase(sigAlg)) {
          break;
        }
      }
      if (i == SIG_ALG.length) {
        nativeFreeCertSignReq(CSR);
        nativeFreeCert(SigningCert);
        RSA.freeRSAKeyPair(SigningRSA);
        throw new SymphonyInputException("Unsupported signature algorithm.");
      }
    }

    long Cert = nativeSignCSR(CSR, SigningRSA, SigningCert, SerialNum.toString(), sigAlg, extArray, ValidStartingInXDays, ValidForXDays);
    if (Cert == 0) {
      nativeFreeCertSignReq(CSR);
      nativeFreeCert(SigningCert);
      RSA.freeRSAKeyPair(SigningRSA);
      throw new SymphonySignatureException("Failed to signCSR");
    }

    String CertPEM = nativeSerializePEMCert(Cert);

    nativeFreeCertSignReq(CSR);
    nativeFreeCert(SigningCert);
    RSA.freeRSAKeyPair(SigningRSA);
    nativeFreeCert(Cert);

    return CertPEM;
  }

	//NEW CSR FUNCTIONS
	public static String generateCSR(String subjectName, String SigningRSAKeyPairPEM) throws SymphonyInputException, SymphonyPEMFormatException, SymphonySignatureException, UnsupportedEncodingException
	{
		return generateCSR(subjectName, SigningRSAKeyPairPEM, null);
	}

	public static String generateCSR(String subjectName, String SigningRSAKeyPairPEM, String signatureAlgorithm) throws SymphonyInputException, SymphonyPEMFormatException, SymphonySignatureException, UnsupportedEncodingException
	{
		if (subjectName == null || SigningRSAKeyPairPEM == null)
			throw new SymphonyInputException("Can not pass null's");

		Utils.validateCString(SigningRSAKeyPairPEM);

		String[] rdns = createListName(subjectName);
		if (rdns == null)
			throw new SymphonyPEMFormatException("Failed to parse given name");

		long SigningRSA = RSA.mallocRSAKeyPairFromPem(SigningRSAKeyPairPEM);
		if (SigningRSA == 0) 
			throw new SymphonyPEMFormatException("Failed to mallocRSAKeyPairFromPem");

		if (signatureAlgorithm != null)
		{
			int i = 0;
			
			for (; i < SIG_ALG.length; i++)
				if (SIG_ALG[i].equalsIgnoreCase(signatureAlgorithm))
					break;
			
			if (i == SIG_ALG.length)
			{
				RSA.freeRSAKeyPair(SigningRSA);
				throw new SymphonyPEMFormatException("Unsupported signature algorithm.");
			}
			
		}
		long CSR = nativeCreateCertSignRequest(signatureAlgorithm, rdns, SigningRSA);
		
		if (CSR == 0) 
		{
			RSA.freeRSAKeyPair(SigningRSA);
			throw new SymphonyPEMFormatException("Failed to nativeCreateCertSignRequest");
		}

		String CSRPEM = nativeSerializePEMCertSignReq(CSR);

		nativeFreeCertSignReq(CSR);
		RSA.freeRSAKeyPair(SigningRSA);

		return CSRPEM;
	}

  public static String createSelfSigned(String PEMEncodedRSAKeyPair, BigInteger SerialNumber, String name, int ValidStartingInXDays, int ValidForXDays) throws SymphonyInputException, SymphonyPEMFormatException, SymphonySignatureException, UnsupportedEncodingException {
    return createSelfSigned(PEMEncodedRSAKeyPair, SerialNumber, name, null, null, ValidStartingInXDays, ValidForXDays);
  }

  public static String createSelfSigned(String PEMEncodedRSAKeyPair, BigInteger SerialNumber, String name, Extensions ext, int ValidStartingInXDays, int ValidForXDays) throws SymphonyInputException, SymphonyPEMFormatException, SymphonySignatureException, UnsupportedEncodingException {
    return createSelfSigned(PEMEncodedRSAKeyPair, SerialNumber, name, null, ext, ValidStartingInXDays, ValidForXDays);
  }
	
  public static String createSelfSigned(String PEMEncodedRSAKeyPair, BigInteger SerialNumber, String name, String sigalg, int ValidStartingInXDays, int ValidForXDays) throws SymphonyInputException, SymphonyPEMFormatException, SymphonySignatureException, UnsupportedEncodingException {
    return createSelfSigned(PEMEncodedRSAKeyPair, SerialNumber, name, sigalg, null, ValidStartingInXDays, ValidForXDays);
  }

  public static String createSelfSigned(String PEMEncodedRSAKeyPair, BigInteger SerialNumber, String name, String sigalg, Extensions ext, int ValidStartingInXDays, int ValidForXDays) throws SymphonyInputException, SymphonyPEMFormatException, SymphonySignatureException, UnsupportedEncodingException {
    if (PEMEncodedRSAKeyPair == null || ValidForXDays < 1 || SerialNumber == null || SerialNumber.compareTo(BigInteger.ONE) < 0)
      throw new SymphonyInputException("can not pass null's, and ValidForXDays must be at least 1 and SerialNumber must be at least 1");

    long RSAKeyPairPtr = RSA.mallocRSAKeyPairFromPem(PEMEncodedRSAKeyPair);
    if (RSAKeyPairPtr == 0)
      throw new SymphonyPEMFormatException("Failed to nativeDeserializeRSAPubKey");

    if (ValidForXDays == 0) {
      RSA.freeRSAKeyPair(RSAKeyPairPtr);
      throw new SymphonyInputException("Your cert needs to be valid for atleast one day.");
    }

    String[] rdns = createListName(name);
    if (rdns == null) {
      RSA.freeRSAKeyPair(RSAKeyPairPtr);
      throw new SymphonyInputException("Invalid DN name.");
    }

    String[] extArray = null;
    if (ext != null && ext.getExtensions().size() > 0) {
      for (int i = 0; i < ext.getCrlExt().length; i++) {
        if (ext.getExtensions().containsKey(ext.getCrlExt()[i])) {
          RSA.freeRSAKeyPair(RSAKeyPairPtr);
          throw new SymphonyInputException("This is an invalid certificate extension: " + ext.getCrlExt()[i]);
        }
      }
      extArray = ext.createArrayExt();
      if (extArray == null) {
        RSA.freeRSAKeyPair(RSAKeyPairPtr);
        throw new SymphonyInputException("Invalid extensions.");
      }
    }

    if (sigalg != null) {
      int i = 0;
      for (; i < SIG_ALG.length; i++) {
        if (SIG_ALG[i].equalsIgnoreCase(sigalg)) {
          break;
        }
      }

      if (i == SIG_ALG.length) {
        RSA.freeRSAKeyPair(RSAKeyPairPtr);
        throw new SymphonyInputException("Unsupported signature algorithm.");
      }
    }

    long cert = nativeCreateSelfSignedCertificate(RSAKeyPairPtr, SerialNumber.toString(), sigalg, rdns, extArray, ValidStartingInXDays, ValidForXDays);
    if (cert == 0)
      throw new SymphonySignatureException("Failed to nativeCreateSelSignedCert");

    String PEMEncodedCert = nativeSerializePEMCert(cert);
    if (PEMEncodedCert == null) {
      RSA.freeRSAKeyPair(RSAKeyPairPtr);
      throw new SymphonyPEMFormatException("Failed to nativeSerializePEMCert");
    }

    nativeFreeCert(cert);
    RSA.freeRSAKeyPair(RSAKeyPairPtr);

    return PEMEncodedCert;
  }

  private static String[] createListName(String name) throws SymphonyInputException {
    String[] pArray = null;
    if (name != null && name.length() > 0) {
      LdapName parsed = null;
      try {
          parsed = new LdapName(name);
      } catch (Exception e) {
          throw new SymphonyInputException("Problem parsing name: " + e);
      }

      if (parsed != null && parsed.size() > 0) {
        pArray = new String[parsed.size()];

        for (int i = 0; i < parsed.size(); i++) {
          pArray[i] = (parsed.getRdns().get(i)).toString();
        }
      }
    }
    return pArray;
  }
}
