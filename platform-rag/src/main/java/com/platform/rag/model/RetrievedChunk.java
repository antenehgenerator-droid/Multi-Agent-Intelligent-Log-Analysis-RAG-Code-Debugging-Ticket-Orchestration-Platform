package com.platform.rag.model;

import java.util.Map;

public record RetrievedChunk(
    String id, String content, double score, Map<String, Object> metadata) {

  public RetrievedChunk {
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
