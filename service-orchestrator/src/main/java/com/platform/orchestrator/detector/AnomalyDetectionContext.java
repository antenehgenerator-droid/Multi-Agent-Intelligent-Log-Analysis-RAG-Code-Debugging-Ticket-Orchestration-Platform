package com.platform.orchestrator.detector;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * Inputs for {@link AnomalyDetector}: per-fingerprint window counts, per-service error counts, and
 * cold-start flag from {@link com.platform.orchestrator.stats.BaselineStore}.
 */
public record AnomalyDetectionContext(
    String service,
    String fingerprint,
    double currentFingerprintCount,
    DescriptiveStatistics fingerprintBaselinePastWindows,
    double currentServiceErrorCount,
    DescriptiveStatistics serviceErrorBaselinePastWindows,
    boolean coldStart24h) {}
