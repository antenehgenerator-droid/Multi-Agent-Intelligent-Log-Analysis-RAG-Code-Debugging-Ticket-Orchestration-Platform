package com.platform.orchestrator.service;

import com.google.common.hash.Hashing;
import com.platform.core.model.ParsedLog;
import com.platform.core.model.Ticket;
import com.platform.orchestrator.detector.AnomalyDetector;
import com.platform.orchestrator.detector.AnomalyReport;
import com.platform.orchestrator.util.StubTicketGenerator;
import com.platform.persistence.entity.IncidentEntity;
import com.platform.persistence.entity.TicketEntity;
import com.platform.persistence.repo.IncidentRepository;
import com.platform.persistence.repo.TicketRepository;
import com.platform.queue.config.KafkaTopicsConfig;
import com.platform.queue.model.KafkaEnvelope;
import com.platform.queue.producer.EnvelopeProducer;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrchestratorService {

  private static final String TRACE_ID_KEY = "traceId";
  private static final String TICKETS_SCHEMA = "tickets.new.v1";

  private final AnomalyDetector detector;
  private final IncidentRepository incidentRepository;
  private final TicketRepository ticketRepository;
  private final EnvelopeProducer ticketProducer;
  private final MeterRegistry meterRegistry;

  public OrchestratorService(
      AnomalyDetector detector,
      IncidentRepository incidentRepository,
      TicketRepository ticketRepository,
      EnvelopeProducer ticketProducer,
      MeterRegistry meterRegistry) {
    this.detector = detector;
    this.incidentRepository = incidentRepository;
    this.ticketRepository = ticketRepository;
    this.ticketProducer = ticketProducer;
    this.meterRegistry = meterRegistry;
  }

  @Transactional
  public void run(ParsedLog log, AnomalyReport anomaly) {
    long startTime = System.currentTimeMillis();

    // Deterministic incident id (UUID) derived from fingerprint+type.
    UUID incidentId = computeIncidentId(log.fingerprint(), anomaly.type().name());

    if (ticketRepository.existsByIncidentId(incidentId)) {
      meterRegistry.counter("pipeline.completed_total", "state", "DISCARDED_DUPLICATE").increment();
      return;
    }

    Instant now = Instant.now();
    IncidentEntity incident =
        new IncidentEntity(
            incidentId,
            UUID.fromString("00000000-0000-0000-0000-000000000000"),
            "Anomaly: " + anomaly.type() + " in " + log.raw().service(),
            "Fingerprint " + log.fingerprint(),
            null,
            "OPEN",
            anomaly.score() > 5 ? "HIGH" : "MEDIUM",
            log.raw().service(),
            log.raw().occurredAt() == null ? now : log.raw().occurredAt(),
            log.raw().occurredAt() == null ? now : log.raw().occurredAt(),
            now);
    if (!incidentRepository.existsById(incidentId)) {
      incidentRepository.save(incident);
    }

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
    ticketRepository.save(entity);

    KafkaEnvelope<Ticket> outgoing =
        new KafkaEnvelope<>(
            TICKETS_SCHEMA,
            UUID.randomUUID(),
            log.raw().occurredAt() == null ? now : log.raw().occurredAt(),
            now,
            MDC.get(TRACE_ID_KEY),
            null,
            ticket);
    ticketProducer.send(KafkaTopicsConfig.TICKETS_NEW, incidentId.toString(), outgoing);

    long latencyMs = System.currentTimeMillis() - startTime;
    meterRegistry.counter("pipeline.completed_total", "state", "TICKETED").increment();
    meterRegistry.summary("orchestrator.latency_ms").record(latencyMs);
  }

  private static UUID computeIncidentId(String fingerprint, String anomalyType) {
    byte[] hash =
        Hashing.sha256()
            .hashString(fingerprint + ":" + anomalyType, StandardCharsets.UTF_8)
            .asBytes();
    return UUID.nameUUIDFromBytes(hash);
  }
}

