package com.platform.core.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// platform-core/.../model/LogIngestionRequest.java
public record LogIngestionRequest(
    @NotEmpty @Size(max = 1000) List<LogEntry> events
) {
    public record LogEntry(
        @NotEmpty String service,
        @NotEmpty String level,
        @NotEmpty @Size(max = 262144) String message, // 256KB limit
        String stackTrace,
        String traceId,
        String spanId,
        Instant occurredAt,
        Map<String, Object> metadata
    ) {}
}
