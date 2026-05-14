package com.platform.orchestrator.detector;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnomalyDetectorTest {

  private AnomalyDetector detector;

  @BeforeEach
  void setUp() {
    detector = new AnomalyDetector(new SimpleMeterRegistry());
  }

  @Test
  void steadyTraffic_noAnomaly() {
    DescriptiveStatistics fp = series(10, 10, 11, 10, 9, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10);
    DescriptiveStatistics svc = series(2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2);
    var ctx =
        new AnomalyDetectionContext("svc", "fp1", 10.0, fp, 2.0, svc, false);
    assertThat(detector.detect(ctx)).isEmpty();
  }

  @Test
  void suddenSpike_zScore() {
    DescriptiveStatistics fp = new DescriptiveStatistics();
    for (int i = 0; i < 35; i++) {
      fp.addValue(5 + (i % 3));
    }
    var ctx = new AnomalyDetectionContext("svc", "fp1", 40.0, fp, 0.0, new DescriptiveStatistics(), false);
    assertThat(detector.detect(ctx))
        .isPresent()
        .get()
        .extracting(AnomalyReport::type)
        .isEqualTo(AnomalyReport.Type.SPIKE);
  }

  @Test
  void gradualRamp_rateJumpRelative() {
    DescriptiveStatistics svc = series(10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10);
    DescriptiveStatistics fp = new DescriptiveStatistics();
    for (int i = 0; i < 35; i++) {
      fp.addValue(1.0);
    }
    var ctx = new AnomalyDetectionContext("svc", "fp1", 1.0, fp, 18.0, svc, false);
    assertThat(detector.detect(ctx))
        .isPresent()
        .get()
        .extracting(AnomalyReport::type)
        .isEqualTo(AnomalyReport.Type.RATE_JUMP);
  }

  @Test
  void newFingerprint() {
    DescriptiveStatistics fp = new DescriptiveStatistics();
    for (int i = 0; i < 35; i++) {
      fp.addValue(0.0);
    }
    var ctx = new AnomalyDetectionContext("svc", "fp-new", 6.0, fp, 0.0, new DescriptiveStatistics(), false);
    assertThat(detector.detect(ctx))
        .isPresent()
        .get()
        .extracting(AnomalyReport::type)
        .isEqualTo(AnomalyReport.Type.NEW_ERROR);
  }

  @Test
  void coldStart_staticSpike() {
    DescriptiveStatistics fp = new DescriptiveStatistics();
    var ctx = new AnomalyDetectionContext("svc", "fp1", 55.0, fp, 0.0, new DescriptiveStatistics(), true);
    assertThat(detector.detect(ctx))
        .isPresent()
        .get()
        .satisfies(
            r -> {
              assertThat(r.type()).isEqualTo(AnomalyReport.Type.SPIKE);
              assertThat(r.evidence().get("strategy")).isEqualTo("cold_start_24h_static");
            });
  }

  private static DescriptiveStatistics series(int... values) {
    DescriptiveStatistics s = new DescriptiveStatistics();
    for (int v : values) {
      s.addValue(v);
    }
    return s;
  }

}
