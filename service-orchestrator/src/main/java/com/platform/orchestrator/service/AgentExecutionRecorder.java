package com.platform.orchestrator.service;

import com.platform.persistence.entity.AgentExecutionEntity;
import com.platform.persistence.repo.AgentExecutionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentExecutionRecorder {

  private final AgentExecutionRepository agentExecutionRepository;

  public AgentExecutionRecorder(AgentExecutionRepository agentExecutionRepository) {
    this.agentExecutionRepository = agentExecutionRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      UUID executionId,
      UUID pipelineId,
      String agentName,
      Instant startedAt,
      String traceId,
      String status,
      long latencyMs) {
    agentExecutionRepository.save(
        new AgentExecutionEntity(
            executionId,
            pipelineId,
            agentName,
            0,
            BigDecimal.ZERO,
            startedAt,
            traceId == null || traceId.isEmpty() ? null : traceId,
            status,
            latencyMs));
  }
}
