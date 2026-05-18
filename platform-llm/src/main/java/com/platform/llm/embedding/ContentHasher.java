package com.platform.llm.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ContentHasher {

  private ContentHasher() {}

  /** Stable SHA-256 hex digest used for deduplication before model calls. */
  public static String hashText(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        String part = Integer.toHexString(0xff & b);
        if (part.length() == 1) {
          hex.append('0');
        }
        hex.append(part);
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  public static String cacheKey(String text, String modelName) {
    return hashText(text + modelName);
  }
}
