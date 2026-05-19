package com.platform.rag.model;

import java.util.Map;

public record RetrievalQuery(
    String text,
    float[] vector,
    String corpus,
    int topK,
    Map<String, Object> filters) {

  public RetrievalQuery {
    filters = filters == null ? Map.of() : Map.copyOf(filters);
  }
}
