package com.gs.ti.wpt.lc.security.cryptolib;

public final class Version {

	static {
		Utils.loadCryptoLib();
	}
	
	// No one should ever create an instance of this class, so
	// the constructor is private.
	private Version() { }
	
	// native code.
	private static native String nativeGetVersion();
	
	// Wrapper.
	public static String get() {
		return nativeGetVersion();
	}
	
}
