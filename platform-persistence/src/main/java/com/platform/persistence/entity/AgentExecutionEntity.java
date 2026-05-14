package com.platform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_executions")
public class AgentExecutionEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "pipeline_id", nullable = false)
  private UUID pipelineId;

  @Column(name = "agent_name", nullable = false)
  private String agentName;

  @Column(name = "tokens_in", nullable = false)
  private int tokensIn;

  @Column(name = "cost_usd", nullable = false)
  private BigDecimal costUsd;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "trace_id")
  private String traceId;

  @Column(name = "status")
  private String status;

  @Column(name = "latency_ms")
  private Long latencyMs;

  protected AgentExecutionEntity() {}

  public AgentExecutionEntity(
      UUID id,
      UUID pipelineId,
      String agentName,
      int tokensIn,
      BigDecimal costUsd,
      Instant startedAt,
      String traceId,
      String status,
      Long latencyMs) {
    this.id = id;
    this.pipelineId = pipelineId;
    this.agentName = agentName;
    this.tokensIn = tokensIn;
    this.costUsd = costUsd;
    this.startedAt = startedAt;
    this.traceId = traceId;
    this.status = status;
    this.latencyMs = latencyMs;
  }
}
