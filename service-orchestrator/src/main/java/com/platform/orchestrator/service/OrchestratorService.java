package com.platform.orchestrator.service;

import com.google.common.hash.Hashing;
import com.platform.core.model.ParsedLog;
import com.platform.core.model.Ticket;
import com.platform.orchestrator.detector.AnomalyDetectionContext;
import com.platform.orchestrator.detector.AnomalyDetector;
import com.platform.orchestrator.detector.AnomalyReport;
import com.platform.orchestrator.stats.BaselineStore;
import com.platform.orchestrator.stats.WindowedFingerprintCounter;
import com.platform.orchestrator.util.StubTicketGenerator;
import com.platform.persistence.entity.IncidentEntity;
import com.platform.persistence.entity.TicketEntity;
import com.platform.persistence.repo.TicketRepository;
import com.platform.queue.config.KafkaTopicsConfig;
import com.platform.queue.model.KafkaEnvelope;
import com.platform.queue.producer.EnvelopeProducer;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class OrchestratorService {

  private static final int BASELINE_WINDOWS_24H = 24 * 60 / 5; // 288 five-minute windows / day
  private static final String TRACE_ID_KEY = "traceId";
  private static final String TICKETS_SCHEMA = "tickets.new.v1";
  private static final String AGENT_NAME = "ORCHESTRATOR";

  private final WindowedFingerprintCounter counter;
  private final BaselineStore baselineStore;
  private final AnomalyDetector detector;
  private final TicketRepository ticketRepository;
  private final TicketWriteService ticketWriteService;
  private final AgentExecutionRecorder agentExecutionRecorder;
  private final EnvelopeProducer ticketProducer;
  private final MeterRegistry meterRegistry;

  public OrchestratorService(
      WindowedFingerprintCounter counter,
      BaselineStore baselineStore,
      AnomalyDetector detector,
      TicketRepository ticketRepository,
      TicketWriteService ticketWriteService,
      AgentExecutionRecorder agentExecutionRecorder,
      EnvelopeProducer ticketProducer,
      MeterRegistry meterRegistry) {
    this.counter = counter;
    this.baselineStore = baselineStore;
    this.detector = detector;
    this.ticketRepository = ticketRepository;
    this.ticketWriteService = ticketWriteService;
    this.agentExecutionRecorder = agentExecutionRecorder;
    this.ticketProducer = ticketProducer;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Statistical anomaly path for one preprocessed log: Redis counters/baseline, optional stub
   * ticket (idempotent on {@code incident_id} hash), Kafka publish, metrics, and {@code
   * agent_executions} audit row (no LLM cost).
   */
  public void run(ParsedLog log) {
    long wallStart = System.currentTimeMillis();
    Instant startedAt = Instant.now();
    UUID executionId = UUID.randomUUID();
    UUID pipelineId = executionId;
    String traceId = traceIdFromMdc();

    try {
      Instant ts = log.raw().occurredAt() == null ? startedAt : log.raw().occurredAt();
      String service = log.raw().service();
      String fingerprint = log.fingerprint();

      baselineStore.ensureFirstSeen(service, fingerprint, ts);
      counter.increment(service, fingerprint, ts);
      if (log.raw().level() != null && "ERROR".equalsIgnoreCase(log.raw().level())) {
        counter.incrementServiceError(service, ts);
      }

      double currentFp = counter.getCount(service, fingerprint, ts);
      DescriptiveStatistics fpBaseline =
          counter.fingerprintBaseline(service, fingerprint, ts, BASELINE_WINDOWS_24H);

      double currentSvcErr = counter.getServiceErrorCount(service, ts);
      DescriptiveStatistics svcBaseline =
          counter.serviceErrorBaseline(service, ts, BASELINE_WINDOWS_24H);

      boolean cold = baselineStore.isColdStart(service, fingerprint, ts);

      AnomalyDetectionContext ctx =
          new AnomalyDetectionContext(
              service, fingerprint, currentFp, fpBaseline, currentSvcErr, svcBaseline, cold);

      Optional<AnomalyReport> report = detector.detect(ctx);
      if (report.isEmpty()) {
        recordTerminal(executionId, pipelineId, traceId, startedAt, "DISCARDED", wallStart);
        return;
      }

      AnomalyReport anomaly = report.get();
      UUID incidentId = computeIncidentId(fingerprint, anomaly.type().name());
      pipelineId = incidentId;

      if (ticketRepository.existsByIncidentId(incidentId)) {
        recordTerminal(executionId, pipelineId, traceId, startedAt, "DISCARDED", wallStart);
        return;
      }

      Instant now = Instant.now();
      IncidentEntity incident =
          new IncidentEntity(
              incidentId,
              UUID.fromString("00000000-0000-0000-0000-000000000000"),
              "Anomaly: " + anomaly.type() + " in " + log.raw().service(),
              "Fingerprint " + fingerprint,
              null,
              "OPEN",
              anomaly.score() > 5 ? "HIGH" : "MEDIUM",
              service,
              log.raw().occurredAt() == null ? now : log.raw().occurredAt(),
              log.raw().occurredAt() == null ? now : log.raw().occurredAt(),
              now);

      Ticket ticket = StubTicketGenerator.from(log, anomaly, incidentId);
      TicketEntity entity =
          new TicketEntity(
              ticket.id(),
              ticket.incidentId(),
              ticket.title(),
              ticket.description(),
              ticket.severity(),
              ticket.service(),
              "[]",
              ticket.fixSuggestion(),
              ticket.externalId(),
              ticket.status().name(),
              ticket.createdAt());
      ticketWriteService.saveIncidentAndTicket(incident, entity, incidentId);

      KafkaEnvelope<Ticket> outgoing =
          new KafkaEnvelope<>(
              TICKETS_SCHEMA,
              UUID.randomUUID(),
              log.raw().occurredAt() == null ? now : log.raw().occurredAt(),
              now,
              traceId.isEmpty() ? null : traceId,
              null,
              ticket);
      ticketProducer.send(KafkaTopicsConfig.TICKETS_NEW, incidentId.toString(), outgoing);

      recordTerminal(executionId, pipelineId, traceId, startedAt, "TICKETED", wallStart);
    } catch (RuntimeException e) {
      long latencyMs = System.currentTimeMillis() - wallStart;
      agentExecutionRecorder.record(
          executionId, pipelineId, AGENT_NAME, startedAt, traceId, "FAILED", latencyMs);
      meterRegistry.counter("pipeline.completed_total", "terminal_state", "FAILED").increment();
      throw e;
    }
  }

  private void recordTerminal(
      UUID executionId,
      UUID pipelineId,
      String traceId,
      Instant startedAt,
      String terminalState,
      long wallStart) {
    long latencyMs = System.currentTimeMillis() - wallStart;
    agentExecutionRecorder.record(
        executionId, pipelineId, AGENT_NAME, startedAt, traceId, terminalState, latencyMs);
    meterRegistry
        .counter("pipeline.completed_total", "terminal_state", terminalState)
        .increment();
    meterRegistry.summary("orchestrator.latency_ms").record(latencyMs);
  }

  private static String traceIdFromMdc() {
    String t = MDC.get(TRACE_ID_KEY);
    return t == null ? "" : t;
  }

  private static UUID computeIncidentId(String fingerprint, String anomalyType) {
    byte[] hash =
        Hashing.sha256()
            .hashString(fingerprint + ":" + anomalyType, StandardCharsets.UTF_8)
            .asBytes();
    return UUID.nameUUIDFromBytes(hash);
  }
}
