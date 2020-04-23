package com.symphony.security.cache;

import com.gs.ti.wpt.lc.security.cryptolib.AES;
import com.gs.ti.wpt.lc.security.cryptolib.SHA;
import com.symphony.security.exceptions.SymphonyEncryptionException;
import com.symphony.security.exceptions.SymphonyInputException;
import com.symphony.security.exceptions.SymphonyNativeException;

import org.bouncycastle.util.Arrays;

import java.util.Random;

public class SecurePersister implements IPersister {

  private final static int KEY_SIZE = 256;
  private final static int TAG_SIZE = 16;

  private byte[] keySeed;
  private byte[] ivSeed;
  private byte[] seed;

  private IPersister persister;

  public SecurePersister(IPersister persister, long randSeed) {

    keySeed = new byte[KEY_SIZE];
    ivSeed = new byte[KEY_SIZE];
    seed = new byte[KEY_SIZE];

    // DO NOT REORDER NEXT 4 LINES.
    Random sr = new Random(randSeed);
    sr.nextBytes(keySeed);
    sr.nextBytes(ivSeed);
    sr.nextBytes(seed);

    this.persister = persister;
  }

  private byte[] getKey(byte[] bufId) throws SymphonyEncryptionException, SymphonyInputException {
    return SHA.SHA2x256(Arrays.concatenate(bufId, keySeed));
  }

  private byte[] getIv(byte[] bufId) throws SymphonyEncryptionException, SymphonyInputException {
    byte[] iv = Arrays.copyOf(SHA.SHA2x256(Arrays.concatenate(bufId, ivSeed)), TAG_SIZE);
    return iv;
  }

  @Override
  public void close() throws SymphonyInputException {
    persister.close();
  }

  @Override
  public void persist(byte[] bufId, byte[] in) throws SymphonyEncryptionException, SymphonyInputException,
      SymphonyNativeException {
    byte[] tag = new byte[TAG_SIZE];
    persister.persist(bufId, Arrays.concatenate(
        AES.EncryptGCM(in, new byte[0], getKey(bufId), getIv(bufId), tag), tag));
  }

  @Override
  public byte[] retrieve(byte[] bufId) throws SymphonyEncryptionException, SymphonyInputException {
    byte[] ciphertextAndTag = persister.retrieve(bufId);
    if (ciphertextAndTag == null)
      return null;
    byte[] plaintext = AES.DecryptGCM(Arrays.copyOfRange(ciphertextAndTag, 0,
        ciphertextAndTag.length
            - TAG_SIZE), new byte[0], getKey(bufId), getIv(bufId), Arrays.copyOfRange(ciphertextAndTag,
        ciphertextAndTag.length - TAG_SIZE, ciphertextAndTag.length));
    return plaintext;
  }

  @Override
  public void delete(byte[] bufId) throws SymphonyNativeException, SymphonyInputException {
    persister.delete(bufId);
  }

  @Override
  public String getType() {
    return "SecurePersister(" + persister.getType() + ")";
  }
}
