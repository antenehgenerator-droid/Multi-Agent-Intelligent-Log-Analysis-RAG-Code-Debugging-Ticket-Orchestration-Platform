package com.platform.orchestrator.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.model.ParsedLog;
import com.platform.orchestrator.detector.AnomalyDetector;
import com.platform.orchestrator.detector.AnomalyReport;
import com.platform.orchestrator.service.OrchestratorService;
import com.platform.orchestrator.stats.WindowedFingerprintCounter;
import com.platform.queue.config.KafkaTopicsConfig;
import com.platform.queue.model.KafkaEnvelope;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class AnomalyOrchestratorConsumer {

  private static final int BASELINE_WINDOWS_24H = 24 * 60 / 5; // 288

  private final WindowedFingerprintCounter counter;
  private final AnomalyDetector detector;
  private final OrchestratorService orchestratorService;
  private final MeterRegistry meterRegistry;
  private final ObjectMapper objectMapper;

  public AnomalyOrchestratorConsumer(
      WindowedFingerprintCounter counter,
      AnomalyDetector detector,
      OrchestratorService orchestratorService,
      MeterRegistry meterRegistry,
      ObjectMapper objectMapper) {
    this.counter = counter;
    this.detector = detector;
    this.orchestratorService = orchestratorService;
    this.meterRegistry = meterRegistry;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(
      topics = KafkaTopicsConfig.PREPROCESSED_LOGS,
      groupId = "anomaly-orchestrator-group",
      containerFactory = "kafkaListenerContainerFactory")
  public void onMessage(@Payload KafkaEnvelope<?> envelope, Acknowledgment ack) {
    try {
      ParsedLog log = coerceParsedLog(envelope == null ? null : envelope.payload());
      Instant ts = envelope != null && envelope.occurredAt() != null ? envelope.occurredAt() : Instant.now();

      counter.increment(log.fingerprint(), ts);
      double currentCount = counter.getCount(log.fingerprint(), ts);
      DescriptiveStatistics baseline = counter.baseline(log.fingerprint(), ts, BASELINE_WINDOWS_24H);

      Optional<AnomalyReport> report = detector.detect(log.fingerprint(), currentCount, baseline);
      if (report.isPresent()) {
        orchestratorService.run(log, report.get());
      } else {
        meterRegistry.counter("pipeline.completed_total", "state", "DISCARDED_NON_ANOMALY").increment();
      }

      ack.acknowledge();
    } catch (RuntimeException e) {
      meterRegistry.counter("pipeline.completed_total", "state", "FAILED").increment();
      throw e;
    }
  }

  private ParsedLog coerceParsedLog(Object payloadObj) {
    if (payloadObj instanceof ParsedLog p) {
      return p;
    }
    if (payloadObj instanceof Map<?, ?> map) {
      return objectMapper.convertValue(map, ParsedLog.class);
    }
    throw new IllegalArgumentException(
        "Unexpected payload type for logs.preprocessed: "
            + (payloadObj == null ? "null" : payloadObj.getClass()));
  }
}

