package com.platform.core.model;

import java.util.List;

/**
 * Output of the RagRetrievalAgent. Contains context fetched from Postgres (Code, Logs, Incidents).
 */
public record RetrievedContext(
    List<Chunk> pastIncidents, List<Chunk> sourceCode, List<Chunk> historicalLogs) {
  public record Chunk(String id, String content, double score, String source) {}
}
