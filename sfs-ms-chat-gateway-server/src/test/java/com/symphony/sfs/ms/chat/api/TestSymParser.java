package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.starter.symphony.stream.SymMessageParser;
import org.junit.Test;

public class TestSymParser {
  @Test
  public void testMessageMD() {
    String msg  = "<div data-format=\"PresentationML\" data-version=\"2.0\">c is inviting you to join a Symphony meeting:<br/> Meeting link: a PIN code: n</div>";
    String msg2 = "<div data-format=\"PresentationML\" data-version=\"2.0\">Fabien Vicente [CES2-DEV] is inviting you to join a Symphony meeting:<br/>Meeting link: https://meet.dev.symphony.com/ogb-zrr-kdjPIN code: 198392</div>";
    SymMessageParser symMessageParser = new com.symphony.sfs.ms.starter.symphony.stream.SymMessageParser(null, null);

    System.out.println(symMessageParser.messageToText(msg, null));
    System.out.println(symMessageParser.messageToText(msg2, null));

  }
}
