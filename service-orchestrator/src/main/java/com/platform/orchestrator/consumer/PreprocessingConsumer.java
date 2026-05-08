package com.platform.orchestrator.consumer;

import com.platform.core.model.LogIngestionRequest.LogEntry;
import com.platform.core.model.ParsedLog;
import com.platform.core.util.Fingerprinter;
import com.platform.core.util.LogParser;
import com.platform.core.util.LogNormalizer;
import com.platform.queue.config.KafkaTopicsConfig;
import com.platform.queue.model.KafkaEnvelope;
import com.platform.queue.producer.EnvelopeProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PreprocessingConsumer {

  private static final Logger log = LoggerFactory.getLogger(PreprocessingConsumer.class);

  private static final String TRACE_ID_KEY = "traceId";
  private static final String PREPROCESSED_SCHEMA = "logs.preprocessed.v1";

  private final LogParser parser;
  private final LogNormalizer normalizer;
  private final Fingerprinter fingerprinter;
  private final EnvelopeProducer producer;
  private final MeterRegistry meterRegistry;
  private final ObjectMapper objectMapper;

  public PreprocessingConsumer(
      LogParser parser,
      LogNormalizer normalizer,
      Fingerprinter fingerprinter,
      EnvelopeProducer producer,
      MeterRegistry meterRegistry,
      ObjectMapper objectMapper) {
    this.parser = parser;
    this.normalizer = normalizer;
    this.fingerprinter = fingerprinter;
    this.producer = producer;
    this.meterRegistry = meterRegistry;
    this.objectMapper = objectMapper;
  }

  @Timed(value = "worker.preprocessing.time", description = "Time taken to preprocess logs")
  @KafkaListener(
      topics = KafkaTopicsConfig.RAW_LOGS,
      groupId = "log-preprocessor-group",
      containerFactory = "kafkaListenerContainerFactory")
  public void onMessage(@Payload KafkaEnvelope<?> envelope, Acknowledgment ack) {
    try {
      if (envelope != null && envelope.traceId() != null) {
        MDC.put(TRACE_ID_KEY, envelope.traceId());
      }

      ParsedLog parsed = parseNormalizeAndFingerprint(envelope);
      String fingerprint = parsed.fingerprint();

      KafkaEnvelope<ParsedLog> outgoing =
          new KafkaEnvelope<>(
              PREPROCESSED_SCHEMA,
              UUID.randomUUID(),
              envelope == null ? Instant.now() : envelope.occurredAt(),
              Instant.now(),
              envelope == null ? null : envelope.traceId(),
              envelope == null ? null : envelope.tenantId(),
              parsed);

      meterRegistry
          .counter("preprocessing.fingerprints_seen_total", "service", parsed.raw().service())
          .increment();
      producer.send(KafkaTopicsConfig.PREPROCESSED_LOGS, fingerprint, outgoing);
      meterRegistry.counter("worker.preprocessing.processed_total").increment();

      ack.acknowledge();
    } finally {
      MDC.remove(TRACE_ID_KEY);
    }
  }

  private ParsedLog parseNormalizeAndFingerprint(KafkaEnvelope<?> envelope) {
    Object payloadObj = envelope == null ? null : envelope.payload();
    LogEntry raw = coerceLogEntry(payloadObj);

    Map<String, Object> structured = parser.parse(raw.message());
    String body = structured.getOrDefault("message", raw.message()).toString();

    String template = normalizer.normalize(body);
    String fingerprint = fingerprinter.compute(raw.service(), raw.level(), template);

    return new ParsedLog(raw, template, fingerprint, structured);
  }

  private LogEntry coerceLogEntry(Object payloadObj) {
    if (payloadObj instanceof LogEntry entry) {
      return entry;
    }
    if (payloadObj instanceof Map<?, ?> map) {
      try {
        return objectMapper.convertValue(map, LogEntry.class);
      } catch (IllegalArgumentException e) {
        meterRegistry.counter("worker.preprocessing.deserialize_failures_total").increment();
        throw new IllegalArgumentException(
            "Failed to convert logs.raw payload map to LogEntry: " + e.getMessage(), e);
      }
    }

    meterRegistry.counter("worker.preprocessing.deserialize_failures_total").increment();
    throw new IllegalArgumentException(
        "Unexpected payload type for logs.raw: " + (payloadObj == null ? "null" : payloadObj.getClass()));
  }
}

