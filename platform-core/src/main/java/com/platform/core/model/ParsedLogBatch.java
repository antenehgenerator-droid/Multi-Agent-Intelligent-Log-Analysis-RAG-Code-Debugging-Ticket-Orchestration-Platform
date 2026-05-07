package com.platform.core.model;

import java.util.List;

/**
 * Output of the LogAnalysisAgent. Groups raw logs into a normalized format with a unified
 * fingerprint.
 */
public record ParsedLogBatch(
    String fingerprint,
    String service,
    String normalizedMessage,
    String baseStackTrace,
    List<LogEvent> rawEvents) {}
