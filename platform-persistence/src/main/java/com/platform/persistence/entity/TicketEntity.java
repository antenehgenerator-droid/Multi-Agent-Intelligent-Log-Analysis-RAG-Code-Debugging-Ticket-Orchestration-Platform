package com.platform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class TicketEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "incident_id")
  private UUID incidentId;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "description", nullable = false)
  private String description;

  @Column(name = "severity", nullable = false)
  private String severity;

  @Column(name = "service", nullable = false)
  private String service;

  @Column(name = "suspected_files", nullable = false)
  private String suspectedFilesJson;

  @Column(name = "fix_suggestion")
  private String fixSuggestion;

  @Column(name = "external_id")
  private String externalId;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected TicketEntity() {}

  public TicketEntity(
      UUID id,
      UUID incidentId,
      String title,
      String description,
      String severity,
      String service,
      String suspectedFilesJson,
      String fixSuggestion,
      String externalId,
      String status,
      Instant createdAt) {
    this.id = id;
    this.incidentId = incidentId;
    this.title = title;
    this.description = description;
    this.severity = severity;
    this.service = service;
    this.suspectedFilesJson = suspectedFilesJson;
    this.fixSuggestion = fixSuggestion;
    this.externalId = externalId;
    this.status = status;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getIncidentId() {
    return incidentId;
  }
}

