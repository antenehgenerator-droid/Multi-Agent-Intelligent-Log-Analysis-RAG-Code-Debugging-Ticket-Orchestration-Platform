package com.platform.orchestrator.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.model.ParsedLog;
import com.platform.orchestrator.service.OrchestratorService;
import com.platform.queue.config.KafkaTopicsConfig;
import com.platform.queue.model.KafkaEnvelope;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class AnomalyOrchestratorConsumer {

  private final OrchestratorService orchestratorService;
  private final ObjectMapper objectMapper;

  public AnomalyOrchestratorConsumer(
      OrchestratorService orchestratorService, ObjectMapper objectMapper) {
    this.orchestratorService = orchestratorService;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(
      topics = KafkaTopicsConfig.PREPROCESSED_LOGS,
      groupId = "anomaly-orchestrator-group",
      containerFactory = "kafkaListenerContainerFactory")
  public void onMessage(@Payload KafkaEnvelope<?> envelope, Acknowledgment ack) {
    ParsedLog log = coerceParsedLog(envelope == null ? null : envelope.payload());
    orchestratorService.run(log);
    ack.acknowledge();
  }

  private ParsedLog coerceParsedLog(Object payloadObj) {
    if (payloadObj instanceof ParsedLog p) {
      return p;
    }
    if (payloadObj instanceof java.util.Map<?, ?> map) {
      return objectMapper.convertValue(map, ParsedLog.class);
    }
    throw new IllegalArgumentException(
        "Unexpected payload type for logs.preprocessed: "
            + (payloadObj == null ? "null" : payloadObj.getClass()));
  }
}
