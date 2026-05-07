package com.platform.core.util;

public class LogNormalizer {
  // Replace UUIDs, Hex IDs, and IPv4 addresses with generic tokens
  private static final String UUID_REGEX =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
  private static final String IP_REGEX = "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}";
  private static final String HEX_REGEX = "0x[0-9a-fA-F]+";

  public String normalize(String message) {
    if (message == null) return "";
    return message
        .replaceAll(UUID_REGEX, "<UUID>")
        .replaceAll(IP_REGEX, "<IP>")
        .replaceAll(HEX_REGEX, "<HEX>");
  }
}
