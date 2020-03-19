package com.symphony.security.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Florin Moisa on 4/13/17.
 */
public class DynamicLibraryLoader {
  private static final Logger LOGGER = LoggerFactory.getLogger(DynamicLibraryLoader.class);

  private static ConcurrentHashMap<String, String> loadedLibraries = new ConcurrentHashMap<>();

  /**
   * Load a jar native resource in the temporary folder and add it to the java.library.path.<br/>
   * The prefix is used to create the temporary file. The file name will have a random number added into it.
   * @param resourcePathInJar
   * @param prefix
   */
  public static synchronized void loadLibrary(String resourcePathInJar, String prefix) {
    loadLibrary(resourcePathInJar, prefix, true);
  }

  /**
   * Load a jar native resource in the temporary folder and add it to the java.library.path.<br/>
   * The prefix is used to create the temporary file.
   *
   * @param resourcePathInJar
   * @param prefix
   * @param randomName
   */
  public static synchronized void loadLibrary(String resourcePathInJar, String prefix, boolean randomName) {
    if(loadedLibraries.containsKey(resourcePathInJar)) return;

    try {
      //for linux and darwin we need lib{name}.{extension} for the actual file
      if(resourcePathInJar.startsWith("lib") || resourcePathInJar.contains("/lib")) {
        prefix = "lib"+prefix;
      }

      //this will be diff for each OS: .dll for win, .so for linux, .dylib for darwin
      String extension = resourcePathInJar.substring(resourcePathInJar.lastIndexOf('.'));

      //the real compiled library
      InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePathInJar);

      //a copy of jar resource on disk to link
      File outputFile = getOuptuFile(prefix, extension, randomName);
      outputFile.deleteOnExit();
      Files.copy(inputStream, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

      //add the temp folder to the path.
      // addDirToJavaLibPath(outputFile.getParentFile().getPath());

      //load the library (need to strip out "lib" prefix and extension from the name)
      String libraryName = outputFile.getName().replace(extension, "");
      if(libraryName.startsWith("lib")) {
        //strip out the lib since the name is without it.
        libraryName = libraryName.replaceFirst("lib", "");
      }
      System.load(outputFile.toString());

      //done, no need to do it twice for this classloader
      loadedLibraries.put(resourcePathInJar, outputFile.getAbsolutePath());
    } catch (Exception e) {
      LOGGER.error("Error loading native crypto lib...", e);
    }

  }

  private static File getOuptuFile(String prefix, String extension, boolean randomName) throws IOException {
    File file = File.createTempFile(prefix, extension);

    if(!randomName) {
      String newFileName = file.getParentFile().getPath() + File.separator + prefix + extension;
      file.delete();
      file = new File(newFileName);
      file.createNewFile();
    }

    return file;
  }

  public static void addDirToJavaLibPath(String s) throws IOException {
    try {
      Field field = ClassLoader.class.getDeclaredField("usr_paths");
      field.setAccessible(true);

      String[] paths = (String[]) field.get(null);

      for (int i = 0; i < paths.length; i++) {
        if (s.equals(paths[i])) {
          return;
        }
      }

      String[] tmp = new String[paths.length + 1];
      System.arraycopy(paths, 0, tmp, 0, paths.length);
      tmp[paths.length] = s;

      field.set(null, tmp);

      String javaLibPath = s + File.pathSeparator + System.getProperty("java.library.path");
      System.setProperty("java.library.path", javaLibPath);

      LOGGER.info("java.library.path={}", javaLibPath);
    }
    catch (IllegalAccessException e) {
      throw new IOException("Failed to get permissions to set library path");
    }
    catch (NoSuchFieldException e) {
      throw new IOException("Failed to get field handle to set library path");
    }
  }
}
