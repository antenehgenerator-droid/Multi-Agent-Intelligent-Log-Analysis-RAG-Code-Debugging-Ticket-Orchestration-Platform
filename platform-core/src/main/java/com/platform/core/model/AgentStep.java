package com.platform.core.model;

import java.time.Instant;
import java.util.UUID;

/** Represents the audit trail and telemetry for a single agent execution. */
public record AgentStep(
    UUID id,
    UUID pipelineId,
    String agentName,
    String inputHash,
    String outputJson, // Serialized to JSON for generic storage
    Status status,
    String error,
    Instant startedAt,
    Instant finishedAt,
    long latencyMs,
    int tokensIn,
    int tokensOut,
    double costUsd,
    String model // e.g., "gpt-4o", "gpt-4o-mini"
    ) {
  public enum Status {
    OK,
    FAILED,
    SKIPPED
  }
}
