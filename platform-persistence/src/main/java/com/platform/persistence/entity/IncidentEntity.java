package com.platform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incidents")
public class IncidentEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "summary", nullable = false)
  private String summary;

  @Column(name = "root_cause")
  private String rootCause;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "severity", nullable = false)
  private String severity;

  @Column(name = "service", nullable = false)
  private String service;

  @Column(name = "first_seen_at", nullable = false)
  private Instant firstSeenAt;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected IncidentEntity() {}

  public IncidentEntity(
      UUID id,
      UUID tenantId,
      String title,
      String summary,
      String rootCause,
      String status,
      String severity,
      String service,
      Instant firstSeenAt,
      Instant lastSeenAt,
      Instant createdAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.title = title;
    this.summary = summary;
    this.rootCause = rootCause;
    this.status = status;
    this.severity = severity;
    this.service = service;
    this.firstSeenAt = firstSeenAt;
    this.lastSeenAt = lastSeenAt;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }
}

