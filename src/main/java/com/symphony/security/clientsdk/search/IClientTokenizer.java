package com.symphony.security.clientsdk.search;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Created by serkan on 5/6/15.
 */
public interface IClientTokenizer {

  List<String> tokenize(String message, Collection<String> hashtags, byte[] privateContentKey,
      byte[] publicContentKey, long rotationId)
      throws NoSuchAlgorithmException, UnsupportedEncodingException, InvalidKeyException;

  Set<String> getPlainTokens(String input);

  Set<String> getHashedTokens(Collection<String> plainTokens, byte[] salt, long rotationId)
      throws UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException;

  byte[] getSalt(byte[] contentKey) throws NoSuchAlgorithmException;
}
