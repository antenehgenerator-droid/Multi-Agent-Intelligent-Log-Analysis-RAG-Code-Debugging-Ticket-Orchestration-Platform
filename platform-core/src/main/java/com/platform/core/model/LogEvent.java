package com.platform.core.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record LogEvent(
    UUID id,
    String service,
    String level,
    String message,
    String stackTrace,
    String traceId,
    String spanId,
    Map<String, Object> metadata,
    String fingerprint,
    Instant occurredAt,
    Instant ingestedAt) {}
