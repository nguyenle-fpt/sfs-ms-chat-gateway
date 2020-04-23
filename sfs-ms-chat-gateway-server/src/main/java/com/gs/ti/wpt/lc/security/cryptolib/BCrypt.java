package com.gs.ti.wpt.lc.security.cryptolib;

import com.symphony.security.exceptions.SymphonyInputException;
import com.symphony.security.exceptions.SymphonyEncryptionException;

import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.util.Arrays;

public class BCrypt {

	static {
		Utils.loadCryptoLib();
	}
	
	// No one should ever create an instance of this class, so
	// the constructor is private.
	private BCrypt() { }
	
	private static int BCRYPT_HASHSIZE = 64;
	private static int RANDBYTES = 16;
	
	// native code.
	private static native int nativeBCryptGenSalt(int Factor, byte[] Salt, byte[] Input);
	private static native int nativeBCryptHashPW(byte[] Passwd, byte[] Salt, byte[] Hash);
	
	// Hash the Password with Factor amount of work (10 is recommended).
	public static String hash(int Factor, String Password) throws SymphonyInputException, SymphonyEncryptionException, UnsupportedEncodingException {
		if (Password == null)
			throw new SymphonyInputException("Invalid parameter(s) - can not pass null's");

		// Add the null terminator to the string, so that the byte[] is a valid C string.
		// This character is not part of the hash (but rather, a delimiter to denote the end
		// of the string).
		Password += "\0";
		
		// Convert UTF-8 string to bytes.
		byte[] PasswordBytes = Password.getBytes("UTF-8");
		
		// Get a secure random for the salt.
		SecureRandom SR = new SecureRandom();
		byte[] Seed = new byte[RANDBYTES];
		SR.nextBytes(Seed);
		
		// Compute the salt.
		byte[] Salt = new byte[BCRYPT_HASHSIZE];
		int retVal = nativeBCryptGenSalt(Factor, Salt, Seed);
		if (retVal != 0)
			throw new SymphonyEncryptionException("Failed in nativeBCryptGenSalt with ret val: " + Integer.toString(retVal));
		
		// Compute the hash, given the salt.
		byte[] Hash = new byte[BCRYPT_HASHSIZE];
		retVal = nativeBCryptHashPW(PasswordBytes, Salt, Hash);
		if (retVal != 0)
			throw new SymphonyEncryptionException("Failed in nativeBCryptHashPW with ret val: " + Integer.toString(retVal));

		return new String (Hash, "UTF-8");
	}
	
	// Returns true iff Hash is the bcrypt hash of Password.
	public static boolean checkPassword(String Password, String Hash) throws SymphonyInputException, SymphonyEncryptionException, UnsupportedEncodingException {
		if (Password == null || Hash == null)
			throw new SymphonyInputException("Invalid parameter(s) - can not pass null's");

		// Add the null terminator to the string, so that the byte[] is a valid C string.
		// This character is not part of the hash (but rather, a delimiter to denote the end
		// of the string).
		Password += "\0";
		Hash += "\0";

		// Convert UTF-8 string to bytes.
		byte[] PasswordBytes = Password.getBytes("UTF-8");
		byte[] HashBytes = Hash.getBytes("UTF-8");

		// The C implementation requires at least BCRYPT_HASHSIZE bytes of play space.
		byte[] PaddedHashBytes = new byte[BCRYPT_HASHSIZE];
		int HashBytesLen = HashBytes.length;
		int PaddedHashBytesLen = PaddedHashBytes.length;
		if (HashBytesLen - 1 > PaddedHashBytesLen)
			throw new SymphonyEncryptionException("Your hash is too long, with a byte length of: " + Integer.toString(HashBytesLen - 1));
		for (int i = 0; i < HashBytesLen && i < PaddedHashBytesLen; ++i) {
			PaddedHashBytes[i] = HashBytes[i];
		}

		// Compute the hash, given the salt.
		byte[] newHash = new byte[BCRYPT_HASHSIZE];
		int retVal = nativeBCryptHashPW(PasswordBytes, PaddedHashBytes, newHash);
		if (retVal != 0)
			throw new SymphonyEncryptionException("Failed in nativeBCryptHashPW with ret val: " + Integer.toString(retVal));

		return Arrays.equals(PaddedHashBytes, newHash);
	}

}
