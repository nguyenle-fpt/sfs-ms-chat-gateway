package com.symphony.security.utils;

import com.symphony.security.exceptions.SymphonyEncryptionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Console;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Decrypts properties based on Java system properties or environment varable.
 * First looks for a system property "encPropertiesPassword", then it looks for an
 * env var defined by a System property "encPropertiesPasswordEnvVar", then looks for an
 * env var called "PROPSPWD"
 * Created by matt.harper on 2/2/17.
 */
public class PropertyDecrypter {

  public static final String PASSWORD_PROMPT = "Property Encryption Password:";
  public static final String PROPERTY_PROMPT = "Property to Encrypt:";
  private static Logger LOG = LoggerFactory.getLogger(PropertyDecrypter.class);

  //
  public static final String DEFAULT_CONFIG_PROPERTY = "encPropertiesPassword";
  public static final String DEFAULT_CONFIG_ENVVAR = "PROPSPWD";
  public static final String OVERRIDE_CONFIG_ENVVAR = "encPropertiesPasswordEnvVar";

  public static final String PREFIX = "ENC(";
  public static final String SUFFIX = ")";

  private PropertyDecrypter() {
  }

  public static char[] getPassword() {
    String ret = System.getProperty(DEFAULT_CONFIG_PROPERTY, null);
    if (ret != null) {
      LOG.debug("Configuration password retrieved from System Property");
      return ret.toCharArray();
    }
    String envVar = System.getProperty(OVERRIDE_CONFIG_ENVVAR, DEFAULT_CONFIG_ENVVAR);
    ret = System.getenv(envVar);
    if (ret != null) {
      LOG.debug("Configuration password retrieved from Environment Variable: " + envVar);
      return ret.toCharArray();
    }
    return null;
  }



  public static Properties getDecryptedProperties(Properties source) throws SymphonyEncryptionException {
    char[] pwd = getPassword();
    Properties ret = new Properties(source);
    final Set<Map.Entry<Object, Object>> entries = source.entrySet();
    for (Map.Entry<Object, Object> e : entries) {
      Object decrypted = decrypt(pwd, e.getValue());
      if (e.getValue() != decrypted) {
        // Only replace it if it has changed.
        ret.put(e.getKey(), decrypted);
      }
    }
    return ret;
  }



  private static Object decrypt(char[] pwd, Object ret) throws SymphonyEncryptionException {
    if (ret == null || !(ret instanceof String)) {
      return ret;
    }
    return decryptString(pwd, (String) ret);
  }

  private static String decryptString(char[] pwd, String ret) throws SymphonyEncryptionException {

    String trimmed = ret.trim();
    if (trimmed.startsWith(PREFIX) && trimmed.endsWith(SUFFIX)) {
      if (pwd == null) {
        throw new SymphonyEncryptionException(
            "Found encrypted property, but no Property Decryption password was not provided");
      }
      return PasswordBasedEncryption.decrypt(pwd,
          trimmed.substring(PREFIX.length(), trimmed.length() - SUFFIX.length()));
    } else {
      return ret;
    }
  }

  public static final String encryptProperty(char[] pwd, String property) throws SymphonyEncryptionException {
    return PREFIX + PasswordBasedEncryption.encrypt(pwd, property) + SUFFIX;
  }

  public static final String encryptProperty(String property) throws SymphonyEncryptionException {
    char[] pwd = getPassword();
    return PREFIX + PasswordBasedEncryption.encrypt(pwd, property) + SUFFIX;
  }

  public static final String encryptPropertyViaPrompt() throws SymphonyEncryptionException {
    return encryptPropertyViaPrompt(new ConsoleUserInput());
  }

  public static final String encryptPropertyViaPrompt( char[] pwd) throws SymphonyEncryptionException {
    return encryptPropertyViaPrompt(new ConsoleUserInput(), pwd);
  }


  protected static final String encryptPropertyViaPrompt(UserInput passwordAndPropertyUserInput) throws SymphonyEncryptionException {
    char[] pwd = passwordAndPropertyUserInput.getSecret(PASSWORD_PROMPT);
    return encryptPropertyViaPrompt(passwordAndPropertyUserInput, pwd);
  }

  protected static final String encryptPropertyViaPrompt(UserInput propertyUserInput, char[] pwd) throws SymphonyEncryptionException {
    char[] property = propertyUserInput.getSecret(PROPERTY_PROMPT);
    return PREFIX + PasswordBasedEncryption.encrypt(pwd, new String(property)) + SUFFIX;
  }

  public static final String encryptPropertyWithPasswordFromEnvAndPropertyFromPrompt()
      throws SymphonyEncryptionException {
    return encryptPropertyWithPasswordFromEnvAndPropertyFromPrompt(new ConsoleUserInput());
  }

  protected static final String encryptPropertyWithPasswordFromEnvAndPropertyFromPrompt(UserInput propertyUserInput) throws SymphonyEncryptionException {
    char[] pwd = getPassword();
    if( pwd == null ){
      throw new IllegalArgumentException("Unable to get password from environment or system property");
    }
    char[] property = propertyUserInput.getSecret("Property to Encrypt:");
    if( property == null || property.length == 0){
      throw new IllegalArgumentException("Property was not provided");
    }
    return PREFIX + PasswordBasedEncryption.encrypt(pwd, new String(property)) + SUFFIX;
  }


  interface UserInput {
    char[] getSecret(String prompt);
  }

  static class ConsoleUserInput implements UserInput {
    @Override
    public char[] getSecret(String prompt) {
      final Console console = System.console();
      if( console == null ){
        throw new RuntimeException("Console is not available");
      }
      return console.readPassword("%s",prompt);
    }
  }

}
