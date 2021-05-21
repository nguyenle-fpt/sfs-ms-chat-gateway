package com.symphony.sfs.ms.chat.util;

import java.util.Arrays;
import java.util.List;

/**
 * Utils Class for special characters
 */
public class SpecialCharactersUtils {

  private static final List<String> specialsCharacters = Arrays.asList("+", "-", "_", "*", "`");

  public static String unescapeSpecialCharacters(String text) {
    for (String specialCharacter : specialsCharacters) {
      text = text.replace("\\" + specialCharacter, specialCharacter);
    }

    return text;
  }
}
