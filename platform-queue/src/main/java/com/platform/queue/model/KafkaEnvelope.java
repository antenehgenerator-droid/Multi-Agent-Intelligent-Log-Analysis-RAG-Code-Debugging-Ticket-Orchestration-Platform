package com.platform.queue.model;

import java.time.Instant;
import java.util.UUID;

public record KafkaEnvelope<T>(
    String schema,
    UUID messageId,
    Instant occurredAt,
    Instant producedAt,
    String traceId,
    String tenantId,
    T payload) {

  public <U> KafkaEnvelope<U> withPayload(U newPayload) {
    return new KafkaEnvelope<>(schema, messageId, occurredAt, producedAt, traceId, tenantId, newPayload);
  }
}
