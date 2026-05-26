package com.platform.agents.util;

public final class JsonPayloads {

  private JsonPayloads() {}

  public static String extractJsonPayload(String raw) {
    int start = raw.indexOf('{');
    int end = raw.lastIndexOf('}');
    if (start >= 0 && end > start) {
      return raw.substring(start, end + 1);
    }
    return raw;
  }
}
