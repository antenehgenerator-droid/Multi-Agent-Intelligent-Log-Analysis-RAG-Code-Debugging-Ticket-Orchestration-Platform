package com.platform.orchestrator.detector;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Service;

@Service
public class AnomalyDetector {

  /** Signal A: z-score on per-window fingerprint frequency vs 24h rolling baseline. */
  private static final double ZSCORE_THRESHOLD = 3.0;

  /** Signal B: first sustained volume for a rare fingerprint (historical mean ~0). */
  private static final double NEW_FINGERPRINT_THRESHOLD = 5.0;

  /** Signal C: per-service error count jump vs rolling baseline (relative or z). */
  private static final double RATE_JUMP_RELATIVE = 1.5;
  private static final double RATE_JUMP_Z = 2.5;

  /** Minimum past windows before trusting z-score (post cold-start wall clock). */
  private static final int MIN_BASELINE_N = 30;

  /** Cold-start (first 24h per fingerprint+service): fixed volume thresholds. */
  private static final double COLD_SPIKE_FP_THRESHOLD = 50.0;

  private static final double COLD_NEW_FP_THRESHOLD = 5.0;
  private static final double COLD_RATE_JUMP_SVC_ERR = 40.0;

  private final MeterRegistry meterRegistry;
  private final DistributionSummary scoreHistogram;

  public AnomalyDetector(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    this.scoreHistogram =
        DistributionSummary.builder("anomaly.score")
            .description("Anomaly detector composite / z-scores")
            .publishPercentileHistogram()
            .register(meterRegistry);
  }

  /**
   * Cold-start mitigation: for the first 24 wall-clock hours after a (service, fingerprint) first
   * appears, we do not trust z-score / service-rate statistics (insufficient history). Instead we
   * use fixed default thresholds ({@value #COLD_SPIKE_FP_THRESHOLD} events per 5 min for spike,
   * etc.) to limit false positives during initial deployment.
   */
  public Optional<AnomalyReport> detect(AnomalyDetectionContext ctx) {
    DescriptiveStatistics fpBase =
        ctx.fingerprintBaselinePastWindows() == null
            ? new DescriptiveStatistics()
            : ctx.fingerprintBaselinePastWindows();
    DescriptiveStatistics svcBase =
        ctx.serviceErrorBaselinePastWindows() == null
            ? new DescriptiveStatistics()
            : ctx.serviceErrorBaselinePastWindows();

    if (ctx.coldStart24h()) {
      return detectColdStart(ctx, fpBase);
    }
    return detectWarm(ctx, fpBase, svcBase);
  }

  private Optional<AnomalyReport> detectColdStart(AnomalyDetectionContext ctx, DescriptiveStatistics fpBase) {
    if (ctx.currentFingerprintCount() >= COLD_SPIKE_FP_THRESHOLD) {
      return Optional.of(
          report(
              AnomalyReport.Type.SPIKE,
              ctx.currentFingerprintCount(),
              evidence(
                  ctx,
                  Map.of(
                      "strategy",
                      "cold_start_24h_static",
                      "threshold",
                      COLD_SPIKE_FP_THRESHOLD,
                      "current",
                      ctx.currentFingerprintCount()))));
    }
    if (fpBase.getMean() == 0.0 && ctx.currentFingerprintCount() > COLD_NEW_FP_THRESHOLD) {
      return Optional.of(
          report(
              AnomalyReport.Type.NEW_ERROR,
              1.0,
              evidence(ctx, Map.of("strategy", "cold_start_24h_static", "current", ctx.currentFingerprintCount()))));
    }
    if (ctx.currentServiceErrorCount() >= COLD_RATE_JUMP_SVC_ERR) {
      return Optional.of(
          report(
              AnomalyReport.Type.RATE_JUMP,
              ctx.currentServiceErrorCount(),
              evidence(
                  ctx,
                  Map.of(
                      "strategy",
                      "cold_start_24h_static",
                      "threshold",
                      COLD_RATE_JUMP_SVC_ERR,
                      "current_service_errors",
                      ctx.currentServiceErrorCount()))));
    }
    return Optional.empty();
  }

  private Optional<AnomalyReport> detectWarm(
      AnomalyDetectionContext ctx, DescriptiveStatistics fpBase, DescriptiveStatistics svcBase) {
    double fpMean = fpBase.getMean();
    double fpStd = fpBase.getStandardDeviation();

    if (fpBase.getN() >= MIN_BASELINE_N && fpStd > 0) {
      double z = (ctx.currentFingerprintCount() - fpMean) / fpStd;
      if (z > ZSCORE_THRESHOLD) {
        return Optional.of(
            report(
                AnomalyReport.Type.SPIKE,
                z,
                evidence(
                    ctx,
                    Map.of(
                        "zScore",
                        z,
                        "current",
                        ctx.currentFingerprintCount(),
                        "mean",
                        fpMean,
                        "stdDev",
                        fpStd,
                        "n",
                        (double) fpBase.getN()))));
      }
    }

    Optional<AnomalyReport> rateJump = detectRateJump(ctx, svcBase);
    if (rateJump.isPresent()) {
      return rateJump;
    }

    if (fpMean == 0.0 && ctx.currentFingerprintCount() > NEW_FINGERPRINT_THRESHOLD) {
      return Optional.of(
          report(
              AnomalyReport.Type.NEW_ERROR,
              1.0,
              evidence(
                  ctx,
                  Map.of(
                      "current",
                      ctx.currentFingerprintCount(),
                      "mean",
                      fpMean,
                      "n",
                      (double) fpBase.getN()))));
    }

    return Optional.empty();
  }

  private Optional<AnomalyReport> detectRateJump(
      AnomalyDetectionContext ctx, DescriptiveStatistics svcBase) {
    double mean = svcBase.getMean();
    double std = svcBase.getStandardDeviation();
    double current = ctx.currentServiceErrorCount();

    if (svcBase.getN() >= MIN_BASELINE_N && std > 0) {
      double z = (current - mean) / std;
      if (z > RATE_JUMP_Z) {
        return Optional.of(
            report(
                AnomalyReport.Type.RATE_JUMP,
                z,
                evidence(
                    ctx,
                    Map.of(
                        "signal",
                        "service_error_z",
                        "zScore",
                        z,
                        "current",
                        current,
                        "mean",
                        mean,
                        "stdDev",
                        std,
                        "n",
                        (double) svcBase.getN()))));
      }
    }

    if (mean > 0 && current >= mean * RATE_JUMP_RELATIVE) {
      double ratio = current / mean;
      return Optional.of(
          report(
              AnomalyReport.Type.RATE_JUMP,
              ratio,
              evidence(
                  ctx,
                  Map.of(
                      "signal",
                      "service_error_relative",
                      "ratio",
                      ratio,
                      "current",
                      current,
                      "mean",
                      mean,
                      "n",
                      (double) svcBase.getN()))));
    }
    return Optional.empty();
  }

  private AnomalyReport report(AnomalyReport.Type type, double score, Map<String, Object> evidence) {
    AnomalyReport report = new AnomalyReport(type, score, evidence);
    recordMetrics(report);
    return report;
  }

  private static Map<String, Object> evidence(AnomalyDetectionContext ctx, Map<String, Object> extra) {
    Map<String, Object> m = new HashMap<>();
    m.put("service", ctx.service());
    m.put("fingerprint", ctx.fingerprint());
    m.putAll(extra);
    return Map.copyOf(m);
  }

  private void recordMetrics(AnomalyReport report) {
    String severity = report.score() > 5 ? "CRITICAL" : "WARNING";
    meterRegistry
        .counter("anomaly.detected_total", "type", report.type().name(), "severity", severity)
        .increment();
    scoreHistogram.record(Math.abs(report.score()));
  }
}

