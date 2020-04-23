package com.gs.ti.wpt.lc.security.cryptolib;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;

public final class PrettyPrint
{
  static
  {
    Utils.loadCryptoLib();
  }

  private PrettyPrint() { }

  private static native String nativePrettyPrint(String data);
  private static native String nativePrettyPrintCert(String cert);
  private static native String nativePrettyPrintCrl(String crl);
  private static native String nativePrettyPrintReq(String req);

  private static String transformHex(String data)
  {
    String transformed = null;
    byte[] newBytes = null;
    List<Byte> bytes = new ArrayList<Byte>();

    for (int i = 0; data != null && i < data.length(); i++) {
      int x = data.charAt(i);
      if (x == '\\') {
        if ((i+3) < data.length() && data.charAt(i+1) == 'x') {
          StringBuffer hexS = new StringBuffer("");
          hexS.append("0x");
          hexS.append(data.charAt(i + 2));
          hexS.append(data.charAt(i + 3));
          char val = (char)Integer.decode(hexS.toString()).intValue();
          bytes.add((byte)val);
          i += 3;
        }
      } else {
        bytes.add((byte)data.charAt(i));
      }
    }
    if (bytes.size() > 0) {
      newBytes = new byte[bytes.size()];
      for (int i = 0; i < bytes.size(); i++) {
        newBytes[i] = bytes.get(i);
      }
      transformed = new String(newBytes, StandardCharsets.UTF_8);
    }
    return transformed;
  }

  public static String prettyPrint(String data) throws Exception
  {
    String tmp = nativePrettyPrint(data);
    return transformHex(tmp);
  }

  public static String prettyPrintCert(String cert)
  {
    String tmp = nativePrettyPrintCert(cert);
    return transformHex(tmp);
  }

  public static String prettyPrintCrl(String crl)
  {
    String tmp = nativePrettyPrintCrl(crl);
    return transformHex(tmp);
  }

  public static String prettyPrintReq(String req)
  {
    String tmp = nativePrettyPrintReq(req);
    return transformHex(req);
  }
}
