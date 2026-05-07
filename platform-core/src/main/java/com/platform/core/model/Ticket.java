package com.platform.core.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Ticket(
    UUID id,
    UUID incidentId,
    String title,
    String description,
    String severity,
    String service,
    List<String> suspectedFiles,
    String fixSuggestion,
    String externalId, // Jira or GitHub Issue key
    Status status,
    Instant createdAt) {
  public enum Status {
    PENDING,
    PUBLISHED,
    EXTERNAL_FAILED
  }
}
