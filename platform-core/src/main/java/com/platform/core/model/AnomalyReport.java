package com.platform.core.model;

/** Output of the AnomalyDetectionAgent. */
public record AnomalyReport(
    ParsedLogBatch logBatch,
    boolean isAnomaly,
    double zScore,
    boolean isNewFingerprint,
    double serviceErrorRateJump,
    String assessmentReason) {}
