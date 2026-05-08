package com.platform.orchestrator.detector;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Service;

@Service
public class AnomalyDetector {

  private static final double ZSCORE_THRESHOLD = 3.0;
  private static final int MIN_BASELINE_N = 30;
  private static final double STATIC_COLD_START_THRESHOLD = 50.0;
  private static final double NEW_FINGERPRINT_THRESHOLD = 5.0;

  private final MeterRegistry meterRegistry;

  public AnomalyDetector(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  /**
   * COLD-START STRATEGY:
   * During the first 24 hours of a new service/fingerprint, the DescriptiveStatistics
   * will have insufficient data (n < 30).
   * FALLBACK: We use a fixed threshold of 50 events/5min as a "Static Baseline"
   * until the rolling window is populated. This prevents false positives
   * during initial deployment.
   */
  public Optional<AnomalyReport> detect(
      String fingerprint, double currentCount, DescriptiveStatistics baseline) {
    DescriptiveStatistics safe = baseline == null ? new DescriptiveStatistics() : baseline;

    if (safe.getN() < MIN_BASELINE_N) {
      if (currentCount >= STATIC_COLD_START_THRESHOLD) {
        AnomalyReport report =
            new AnomalyReport(
                AnomalyReport.Type.SPIKE,
                1.0,
                Map.of(
                    "fingerprint", fingerprint,
                    "current", currentCount,
                    "strategy", "cold_start_static_threshold",
                    "threshold", STATIC_COLD_START_THRESHOLD,
                    "n", safe.getN()));
        recordMetrics(report);
        return Optional.of(report);
      }
    }

    double mean = safe.getMean();
    double stdDev = safe.getStandardDeviation();

    // Signal A: Z-score spike detection
    if (stdDev > 0) {
      double zScore = (currentCount - mean) / stdDev;
      if (zScore > ZSCORE_THRESHOLD) {
        AnomalyReport report =
            new AnomalyReport(
                AnomalyReport.Type.SPIKE,
                zScore,
                Map.of(
                    "fingerprint", fingerprint,
                    "current", currentCount,
                    "mean", mean,
                    "stdDev", stdDev,
                    "n", safe.getN()));
        recordMetrics(report);
        return Optional.of(report);
      }
    }

    // Signal B: New fingerprint (near-zero historical baseline but meaningful current volume)
    if (mean == 0.0 && currentCount > NEW_FINGERPRINT_THRESHOLD) {
      AnomalyReport report =
          new AnomalyReport(
              AnomalyReport.Type.NEW_ERROR,
              1.0,
              Map.of("fingerprint", fingerprint, "current", currentCount, "mean", mean, "n", safe.getN()));
      recordMetrics(report);
      return Optional.of(report);
    }

    return Optional.empty();
  }

  private void recordMetrics(AnomalyReport report) {
    String severity = report.score() > 5 ? "CRITICAL" : "WARNING";
    meterRegistry
        .counter("anomaly.detected_total", "type", report.type().name(), "severity", severity)
        .increment();
    meterRegistry.summary("anomaly.score").record(report.score());
  }
}

