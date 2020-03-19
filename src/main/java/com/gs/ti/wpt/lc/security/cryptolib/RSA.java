package com.gs.ti.wpt.lc.security.cryptolib;

import com.symphony.security.exceptions.SymphonyEncryptionException;
import com.symphony.security.exceptions.SymphonyInputException;
import com.symphony.security.exceptions.SymphonyPEMFormatException;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

public final class RSA {

	static {
		Utils.loadCryptoLib();
	}
	
	// No one should ever create an instance of this class, so
	// the constructor is private.
	private RSA() { }

	// Padding?
	public final static int CL_RSA_PKCS1V15_PADDING = 0;
	public final static int CL_RSA_PKCS1V20_OAEP_PADDING = 1;

	// Due to really crappy reasons, all opaque pointers are long's.
	private static native long nativeInitRSAKeyPair(byte[] Seed, int KeyLength);
	
	private static native String nativeSerializeRSAPubKey(long RSAPubKey);

	private static native String nativeSerializeRSAKeyPair(long RSAKeyPair);
	
	private static native long nativeDeserializeRSAPubKey(String PubKeyBuf);

	private static native long nativeDeserializeRSAKeyPair(String KeyPairBuf);
	
	private static native void nativeFreeRSA(long RSA);

	private static native int nativeGetRSAKeySize(long RSA);

	private static native int nativeDecryptRSA(long RSA, int Pad, byte[] In, byte[] Out);

	private static native int nativeEncryptRSA(long RSAPub, int Pad, byte[] In, byte[] Out);

	private static native int nativeSignRSA(long RSA, int Pad, byte[] In, byte[] Out);

	private static native int nativeVerifyRSA(long RSAPub, int Pad, byte[] Sig, byte[] In);
	
	// DO NO USE THIS unless you know exactly what you're doing. Requires manual memory management.
	public static long mallocRSAKeyPairFromPem(String PEM) throws SymphonyInputException,
			SymphonyPEMFormatException, UnsupportedEncodingException {
		if (PEM == null) {
			throw new SymphonyInputException("Invalid parameter(s) - can not pass null's");
		}
		Utils.validateCString(PEM);

		long PubRSAPtr = nativeDeserializeRSAKeyPair(PEM);
		if (PubRSAPtr == 0) {
			throw new SymphonyPEMFormatException("Failed to nativeDeserializeRSAPubKey");
		}

		return PubRSAPtr;
	}

	// DO NOT USE THIS unless you know exactly what you're doing. Requires manual memory managmeent.
	public static void freeRSAKeyPair(long PubRSA) throws SymphonyInputException {
		if (PubRSA == 0) {
			throw new SymphonyInputException("Invalid parameter(s) - can not pass null's");
		}
		nativeFreeRSA(PubRSA);
	}

	// DO NOT USE THIS unless you know exactly what you're doing. Requires manual memory managmeent.
	public static String serializeRSAPubKey(long PubRSA) throws SymphonyInputException {
		if (PubRSA == 0) {
			throw new SymphonyInputException("Invalid parameter(s) - can not pass null's");
		}
		return nativeSerializeRSAPubKey(PubRSA);
	}


	// Returns the RSA KeyPair in PEM format.
	public static String GenerateKey(byte[] RandomSeed, int KeyLengthInBits) throws SymphonyInputException,
			SymphonyEncryptionException, SymphonyPEMFormatException {
		if (RandomSeed == null)
			throw new SymphonyInputException("Invalid parameter(s) - can not pass null's");

		if (RandomSeed.length * 8 < KeyLengthInBits) {
			throw new SymphonyInputException("Not enough entropy presented for a KeyLength of " + Integer.toString(KeyLengthInBits));
		}
		
		long RSAKeyPair = nativeInitRSAKeyPair(RandomSeed, KeyLengthInBits);
		if (RSAKeyPair == 0) {
			throw new SymphonyEncryptionException("nativeInitRSAKeyPair failed on key gen of length: " + Integer.toString(KeyLengthInBits) + ", with RandomSeed.length of " + Integer.toBinaryString((RandomSeed.length)));
		}
		
		String RSAKeyPairPEM = nativeSerializeRSAKeyPair(RSAKeyPair);
		nativeFreeRSA(RSAKeyPair);
		if (RSAKeyPairPEM.length() == 0)  {
			throw new SymphonyPEMFormatException("nativeSerializeRSAKeyPair failed.");
		}
		return RSAKeyPairPEM;
		
	}
	
	// Returns the RSA Public Key in PEM format.
	public static String getPublicKey(String RSAKeyPairPEM) throws SymphonyInputException, SymphonyPEMFormatException, UnsupportedEncodingException {
		if (RSAKeyPairPEM == null)
			throw new SymphonyInputException("Invalid parameter(s) - can not pass null's");

		Utils.validateCString(RSAKeyPairPEM);
		
		long RSAKeyPair = nativeDeserializeRSAKeyPair(RSAKeyPairPEM);
		if (RSAKeyPair == 0)  {
			throw new SymphonyPEMFormatException("nativeDeserializeRSAKeyPair failed.");
		}
		
		String RSAPubKeyPEM = nativeSerializeRSAPubKey(RSAKeyPair);
		nativeFreeRSA(RSAKeyPair);
		if (RSAPubKeyPEM.length() == 0) {
			throw new SymphonyPEMFormatException("nativeSerializeRSAPubKey failed.");
		}
		return RSAPubKeyPEM;
		
	}
	
	// Helper function that should be used to deserialize PEM formated keys.
	private static long DeserializePubOrPrivPEM(String PEM) {
		if (PEM.startsWith("-----BEGIN PUBLIC KEY-----")) {
			return nativeDeserializeRSAPubKey(PEM);
		} else {
			return nativeDeserializeRSAKeyPair(PEM);
		}
	}
	
	// Returns the In encrypted with RSAPublicKey (PEM encoding of the key) with padding Padding.
	// Throws Exception on error.
	public synchronized static byte[] Encrypt(String PEM, byte[] In, int Padding) throws SymphonyInputException, SymphonyEncryptionException, SymphonyPEMFormatException, UnsupportedEncodingException {
		if (PEM == null || In == null || (Padding != CL_RSA_PKCS1V15_PADDING && Padding != CL_RSA_PKCS1V20_OAEP_PADDING ))
			throw new SymphonyInputException("Invalid parameter(s) - can not pass null's");
		Utils.validateCString(PEM);
		
		long RSAPubKey = DeserializePubOrPrivPEM(PEM);
		if (RSAPubKey == 0) {
			throw new SymphonyPEMFormatException("DeserializePubOrPrivPEM failed with PEM formated key: " + PEM);
		}

		int RSASize = nativeGetRSAKeySize(RSAPubKey);
		
		byte[] Out = new byte[RSASize];
		int retVal = nativeEncryptRSA(RSAPubKey, Padding, In, Out);
		nativeFreeRSA(RSAPubKey);
		if (retVal != 0) {
			throw new SymphonyEncryptionException("nativeEncryptRSA returned: " + Integer.toString(retVal) + ", with padding: " + Integer.toString(Padding) + ", KeySize: " + Integer.toString(RSASize));
		}
		
		return Out;
	}
	
	// Returns In decrypted with RSAPrivateKey (PEM encoding of the key) with padding Padding.
	// Throws exception on error.
	public synchronized static byte[] Decrypt(String RSAPrivateKeyPEM, byte[] In, int Padding) throws SymphonyInputException, SymphonyEncryptionException, SymphonyPEMFormatException, UnsupportedEncodingException {
		if (RSAPrivateKeyPEM == null || In == null || (Padding != CL_RSA_PKCS1V15_PADDING && Padding != CL_RSA_PKCS1V20_OAEP_PADDING ))
			throw new SymphonyInputException("Invalid parameter(s) - can not pass null's");
		Utils.validateCString(RSAPrivateKeyPEM);
		
		long RSAKeyPair = nativeDeserializeRSAKeyPair(RSAPrivateKeyPEM);
		if (RSAKeyPair == 0) {
			throw new SymphonyPEMFormatException("nativeDeserializeRSAKeyPair returned 0 on input: " + Utils.bytesToHex(In) + ", with padding: " + Integer.toString(Padding));
		}
		
		int RSADecrtypSize = nativeGetRSAKeySize(RSAKeyPair);

		byte[] Out = new byte[RSADecrtypSize];
		int retVal = nativeDecryptRSA(RSAKeyPair, Padding, In, Out);
		nativeFreeRSA(RSAKeyPair);
		if (retVal < 0) {
			throw new SymphonyEncryptionException("nativeDecryptRSA returned: " + Integer.toString(retVal) + ", with padding: " + Integer.toString(Padding));
		}
		
		return Arrays.copyOf(Out, retVal);
	}
	
	// Signs In with RSAPrivateKeyPEM, returns Signature
	public static byte[] Sign(String RSAPrivateKeyPEM, byte[] In, int Padding) throws SymphonyInputException, SymphonyEncryptionException, SymphonyPEMFormatException, UnsupportedEncodingException {
		if (RSAPrivateKeyPEM == null || In == null || (Padding != CL_RSA_PKCS1V15_PADDING && Padding != CL_RSA_PKCS1V20_OAEP_PADDING ))
			throw new SymphonyInputException("Invalid parameter(s) - can not pass null's");
		Utils.validateCString(RSAPrivateKeyPEM);
		
		long RSAKeyPair = nativeDeserializeRSAKeyPair(RSAPrivateKeyPEM);
		if (RSAKeyPair == 0) {
			throw new SymphonyPEMFormatException("nativeDeserializeRSAKeyPair returned 0 on input: " + Utils.bytesToHex(In) + ", with padding: " + Integer.toString(Padding));
		}

		int RSASigSize = nativeGetRSAKeySize(RSAKeyPair);
		byte[] Out = new byte[RSASigSize];

		int retVal = nativeSignRSA(RSAKeyPair, Padding, In, Out);
		nativeFreeRSA(RSAKeyPair);
		if (retVal != 0) {
			throw new SymphonyEncryptionException("nativeSignRSA returned: " + Integer.toString(retVal) + ", with padding: " + Integer.toString(Padding));
		}

		return Out;
	}
	
	// Verifies Signature of In with RSAPublicKeyPEM
	public static boolean Verify(String PEM, byte[] In, byte[] Signature, int Padding) throws SymphonyInputException, SymphonyEncryptionException, SymphonyPEMFormatException, UnsupportedEncodingException {
		if (PEM == null || In == null || Signature == null || (Padding != CL_RSA_PKCS1V15_PADDING && Padding != CL_RSA_PKCS1V20_OAEP_PADDING ))
			throw new SymphonyInputException("Invalid parameter(s) - can not pass null's");
		Utils.validateCString(PEM);
		
		long RSAPubKey = DeserializePubOrPrivPEM(PEM);
		if (RSAPubKey == 0) {
			throw new SymphonyPEMFormatException("DeserializePubOrPrivPEM returned 0 on input: " + PEM);
		}
		
		int retVal = nativeVerifyRSA(RSAPubKey, Padding, Signature, In);
		nativeFreeRSA(RSAPubKey);
		if (retVal == 0) {
			return true;
		} else if (retVal == 1) {
			return false;
		} else {
			throw new SymphonyEncryptionException("nativeVerifyRSA returned an error: " + Integer.toString(retVal) + ", with padding: " + Integer.toString(Padding));
		}
		
		
		
	}
}
