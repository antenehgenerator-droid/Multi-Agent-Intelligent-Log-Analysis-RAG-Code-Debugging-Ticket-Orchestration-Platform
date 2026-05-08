package com.platform.queue.producer;

import com.platform.queue.config.KafkaEnvelopeProducerAutoConfiguration;
import com.platform.queue.model.KafkaEnvelope;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/**
 * Wraps {@link KafkaTemplate} so {@link KafkaEnvelope} messages get consistent metadata on the
 * record (values are still in the body; headers mirror trace/tenant for consumers that read headers
 * only).
 *
 * <p>Prefer {@code application} properties for idempotent producers, e.g. {@code
 * spring.kafka.producer.acks=all} and {@code
 * spring.kafka.producer.properties.enable.idempotence=true}. Defaults are also set on the envelope
 * {@link org.springframework.kafka.core.ProducerFactory} auto-configuration.
 */
@Service
public class EnvelopeProducer {

  private final KafkaTemplate<String, KafkaEnvelope<?>> kafkaTemplate;

  public EnvelopeProducer(
      @Qualifier(KafkaEnvelopeProducerAutoConfiguration.ENVELOPE_KAFKA_TEMPLATE_BEAN_NAME)
          KafkaTemplate<String, KafkaEnvelope<?>> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  /**
   * Sends an envelope to Kafka. Generic payload is erased at runtime; the producer serializes via
   * JSON.
   */
  public <T> CompletableFuture<SendResult<String, KafkaEnvelope<?>>> sendEnvelope(
      String topic, String key, KafkaEnvelope<T> envelope) {
    KafkaEnvelope<?> outgoing = envelope;
    ProducerRecord<String, KafkaEnvelope<?>> record = new ProducerRecord<>(topic, key, outgoing);
    if (envelope.traceId() != null && !envelope.traceId().isEmpty()) {
      record.headers().add("traceId", envelope.traceId().getBytes(StandardCharsets.UTF_8));
    }
    if (envelope.tenantId() != null && !envelope.tenantId().isEmpty()) {
      record.headers().add("tenantId", envelope.tenantId().getBytes(StandardCharsets.UTF_8));
    }
    return kafkaTemplate.send(record);
  }

  /** Fire-and-forget variant (same semantics as {@link #sendEnvelope}). */
  public <T> void send(String topic, String key, KafkaEnvelope<T> envelope) {
    sendEnvelope(topic, key, envelope);
  }
}
