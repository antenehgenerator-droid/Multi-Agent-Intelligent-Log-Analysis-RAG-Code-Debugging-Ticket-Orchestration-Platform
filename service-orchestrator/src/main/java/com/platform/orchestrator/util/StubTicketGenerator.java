package com.platform.orchestrator.util;

import com.platform.core.model.ParsedLog;
import com.platform.core.model.Ticket;
import com.platform.orchestrator.detector.AnomalyReport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class StubTicketGenerator {
  private StubTicketGenerator() {}

  public static Ticket from(ParsedLog log, AnomalyReport report, UUID incidentId) {
    String severity = report.score() > 5 ? "HIGH" : "MEDIUM";
    return new Ticket(
        UUID.randomUUID(),
        incidentId,
        "Anomaly Detected: " + report.type() + " in " + log.raw().service(),
        "Fingerprint: " + log.fingerprint() + "\nEvidence: " + report.evidence(),
        severity,
        log.raw().service(),
        List.of(),
        null,
        null,
        Ticket.Status.PENDING,
        Instant.now());
  }
}

