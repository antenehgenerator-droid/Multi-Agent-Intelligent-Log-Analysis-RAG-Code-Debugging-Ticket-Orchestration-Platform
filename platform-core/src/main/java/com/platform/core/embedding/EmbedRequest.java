package com.platform.core.embedding;

import java.util.Map;

public record EmbedRequest(
    EmbedCorpus corpus,
    String contentId,
    String text,
    Map<String, String> metadata) {

  public EmbedRequest {
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
