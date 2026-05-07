package com.platform.core.model;

import java.time.Instant;
import java.util.UUID;

public record Incident(
    UUID id,
    String title,
    String summary,
    String rootCause,
    String resolution,
    Status status,
    String severity, // e.g., "P0", "P1"
    String service,
    Instant firstSeenAt,
    Instant lastSeenAt,
    Instant createdAt) {
  public enum Status {
    OPEN,
    RESOLVED,
    DUPLICATE
  }
}
