package com.gs.ti.wpt.lc.security.cryptolib;

import com.symphony.security.utils.DynamicLibraryLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class Utils {
	private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

	private static boolean initialized = false;
	public static String bytesToHex(byte[] in) {
	    final StringBuilder builder = new StringBuilder();
	    builder.append("0x");
	    for(byte b : in) {
	        builder.append(String.format("%02x", b));
	    }
	    return builder.toString();
	}


	
	public static void validateCString(String in) throws UnsupportedEncodingException {
		if (!isASCII(in))
			throw new UnsupportedEncodingException("String is not a ASCII encoded string.");
	}

	private static boolean isASCII(String in) {
		for(char c:in.toCharArray()) {
			if(c < 0 || c > 127) return false;
		}

		return true;
	}

	static String getDynamicPathToJniLib() throws IOException {
		//calculate dynamically the path to the the Crypto JNI lib
		String osArch = System.getProperty("os.arch").toLowerCase();
		String osName = System.getProperty("os.name").toLowerCase();

		LOGGER.info("Arch: {}/{}", osName, osArch);

		if(osName.contains("linux")) {
			if (osArch.contains("64")) {
				return "cryptolibs/linux/x86_64/libsymphonycryptolibjni.so";
			}
			else {
				throw new IOException("We don't support 32 bit");
			}
		}
		else if(osName.contains("windows")) {
			if (osArch.contains("64")) {
				return "cryptolibs/windows/x86_64/symphonycryptolibjni.dll";
			}
			else {
				throw new IOException("We don't support 32 bit");
			}
		}

		return "cryptolibs/darwin/x86_64/libsymphonycryptolibjni.dylib";
	}


	public static void loadCryptoLib() {
		try {
			DynamicLibraryLoader.loadLibrary(getDynamicPathToJniLib(), "symphony-crypto-jni", false);
		} catch (Exception e) {
			LOGGER.error("Could not load cryptolib", e);
		}
	}
}
