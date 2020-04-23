package com.symphony.security.clientsdk.search;


import com.symphony.security.utils.CryptoHashUtils;
import org.apache.commons.lang.ArrayUtils;
import org.bouncycastle.util.encoders.Base64;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by serkan on 5/18/15.
 */
//Simplified tokenizer which supports most of the utf8 characters
public class ClientTokenizer_v2 implements IClientTokenizer {

  private static final String REGEX = "[[\\p{Punct} || [’] || [^\\p{L}^\\d] ]&&[^@_]]";
  private static final Pattern pattern = Pattern.compile(REGEX);
  // This contains all the unicode from the language_unicode.json file in resources folder
  private static final String LANGUAGE_UNICODE = "\u2E80-\u2EFF\u2F00-\u2FDF\u3000-\u303F\u31C0-\u31EF\u3200-\u32FF\u3300-\u33FF\u3400-\u3FFF\u4000-\u4DBF\u4E00-\u4FFF\u5000-\u5FFF\u6000-\u6FFF\u7000-\u7FFF\u8000-\u8FFF\u9000-\u9FFF\uF900-\uFAFF\uFE10-\uFE1F\uFE30-\uFE4F\u3040-\u309F\u30A0-\u30FF\u31F0-\u31FF\u3190-\u319F\uFF00-\uFFEF";
  private static final String CJK_REGEX = "[^"+ LANGUAGE_UNICODE +"]"; // to get the CJK char
  private static final String NON_CJK_REGEX = "["+ LANGUAGE_UNICODE +"]"; // to get non CJK char
  private static final int N_GRAM = 2;
  private static final Pattern CJK = Pattern.compile(CJK_REGEX, Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);
  private static final Pattern NON_CJK = Pattern.compile(NON_CJK_REGEX, Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);

  @Override
  public List<String> tokenize(String text, Collection<String> hashtags, byte[] privateContentKey,
      byte[] publicContentKey, long rotationId) throws NoSuchAlgorithmException,
      UnsupportedEncodingException, InvalidKeyException {
    List<String> tokenIds = new ArrayList<>();

    byte[] privateSalt = getSalt(privateContentKey);
    byte[] publicSalt = getSalt(publicContentKey);

    Set<String> plainTokens = getPlainTokens(text);
    Set<String> hashedPlainTokens = getHashedTokens(plainTokens, privateSalt, rotationId);
    Set<String> hashedEntityTokens = getHashedTokens(hashtags, publicSalt, rotationId);
    tokenIds.addAll(hashedPlainTokens);
    tokenIds.addAll(hashedEntityTokens);
    Collections.shuffle(tokenIds);
    return tokenIds;
  }

  @Override
  public Set<String> getPlainTokens(String input) {
    Set<String> tokens = new HashSet<>();
    if (input == null)
      return tokens;
    String lowerCase = input.toLowerCase();
    Matcher matcher = pattern.matcher(lowerCase);
    String analyzed = matcher.replaceAll(" ");
    String[] strTokens = analyzed.split("\\s+");
    for (String token : strTokens) {
      if (!"".equals(token)) { // should not occur.
        Set<String> cjkBigram = getBigrams(token);
        tokens.addAll(cjkBigram);
        tokens.add(token);
      }
    }
    return tokens;
  }

  private Set<String> getBigrams(String token) {
    Set<String> tokens = new HashSet<>();
    Matcher cjkMatcher = CJK.matcher(token);
    String stringCJK = cjkMatcher.replaceAll(" ");
    String[] cjkTokens = stringCJK.split("\\s+");
    if (cjkTokens.length > 0) {
      for (String cjkToken : cjkTokens) {
        if (cjkToken.length() <= 2) {
          tokens.add(cjkToken);
        } else {
          List<String> biToken = nGrams(N_GRAM, cjkToken);
          tokens.addAll(biToken);
        }
      }

      Matcher nonCJKMatcher = NON_CJK.matcher(token);
      String stringNonCJK = nonCJKMatcher.replaceAll(" ");
      String[] nonCJKToken = stringNonCJK.split("\\s+");
      List<String> list = new ArrayList<>(Arrays.asList(nonCJKToken));
      tokens.addAll(list);
      tokens.removeAll(Arrays.asList(null, ""));
    }
    return tokens;
  }

  @Override
  public Set<String> getHashedTokens(Collection<String> plainTokens, byte[] salt, long rotationId)
      throws UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException {
    Set<String> hashed = new HashSet<>();
    if(plainTokens == null || plainTokens.isEmpty()){
      return hashed;
    }

    for (String s : plainTokens) {
      String hashedToken;
      if(rotationId == 0) {
        hashedToken = hashSHA256(s, salt);
      } else {
        hashedToken = hashHMAC256B64(s,salt);
      }
      hashed.add(hashedToken);
    }
    return hashed;
  }

  public static String hashSHA256(String plainTextToken, byte[] selectedSalt) throws UnsupportedEncodingException, NoSuchAlgorithmException {
    byte[] plainTextTokenBytes = plainTextToken.getBytes("UTF-8");//excluding '\0'
    byte[] concat = ArrayUtils.addAll(selectedSalt, plainTextTokenBytes);
    byte[] out = CryptoHashUtils.sha256digest(concat);
    String b64 = Base64.toBase64String(out);
    return b64;
  }

  public static String hashHMAC256B64(String plainTextToken, byte[] selectedSalt)
      throws UnsupportedEncodingException, InvalidKeyException, NoSuchAlgorithmException {
    byte[] plainTextTokenBytes = plainTextToken.getBytes("UTF-8");//excluding '\0'
    byte[] out = CryptoHashUtils.HMACxSHA256(selectedSalt, plainTextTokenBytes);
    if (out == null)
      return null;
    return Base64.toBase64String(out);
  }

  @Override
  public byte[] getSalt(byte[] contentKey) throws NoSuchAlgorithmException {
    return CryptoHashUtils.sha256digest(contentKey);

  }

  private List<String> nGrams(int nGram, String token) {
    List<String> ngrams = new ArrayList<>();
    for (int i = 0; i < token.length() - nGram + 1; i++) {
      ngrams.add(token.substring(i, i + nGram));
    }
    return ngrams;
  }
}
