package com.platform.worker.model;

import java.util.Map;

public record EmbedRequest(
    String corpus,
    String contentId,
    String text,
    Map<String, String> metadata) {

  public EmbedRequest {
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
