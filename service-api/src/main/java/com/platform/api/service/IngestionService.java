package com.platform.api.service;

import com.platform.api.exception.IngestionQueueException;
import com.platform.core.model.LogIngestionRequest.LogEntry;
import com.platform.core.util.LogNormalizer;
import com.platform.queue.config.KafkaTopicsConfig;
import com.platform.queue.model.KafkaEnvelope;
import com.platform.queue.producer.EnvelopeProducer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionService {

  private static final String RAW_SCHEMA = "logs.raw.v1";
  private static final Duration KAFKA_SEND_DEADLINE = Duration.ofMillis(2500);

  private final SimpleJdbcInsert jdbcInsert;
  private final LogNormalizer normalizer;
  private final ObjectProvider<EnvelopeProducer> envelopeProducer;
  private final MeterRegistry meterRegistry;
  private final Timer ingestionLatencyTimer;
  private final boolean kafkaDualWrite;
  private final String defaultTenantId;

  public IngestionService(
      DataSource dataSource,
      LogNormalizer normalizer,
      ObjectProvider<EnvelopeProducer> envelopeProducer,
      MeterRegistry meterRegistry,
      @Value("${com.platform.ingestion.kafka.dual-write:true}") boolean kafkaDualWrite,
      @Value("${com.platform.ingestion.default-tenant-id:default}") String defaultTenantId) {
    this.jdbcInsert =
        new SimpleJdbcInsert(dataSource)
            .withTableName("log_events")
            .usingColumns("service", "level", "message", "fingerprint", "occurred_at");
    this.normalizer = normalizer;
    this.envelopeProducer = envelopeProducer;
    this.meterRegistry = meterRegistry;
    this.ingestionLatencyTimer =
        Timer.builder("ingestion.latency")
            .description("Latency of ingest batch: JDBC batch + optional Kafka dual-write")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
    this.kafkaDualWrite = kafkaDualWrite;
    this.defaultTenantId = defaultTenantId;
  }

  @Transactional
  public void ingest(List<LogEntry> entries) {
    ingestionLatencyTimer.record(() -> ingestInternal(entries));
  }

  private void ingestInternal(List<LogEntry> entries) {
    List<Map<String, Object>> batch =
        entries.stream()
            .<Map<String, Object>>map(
                e -> {
                  String norm = normalizer.normalize(e.message());
                  Map<String, Object> row = new java.util.HashMap<>();
                  row.put("service", e.service());
                  row.put("level", e.level());
                  row.put("message", e.message());
                  row.put("fingerprint", computeFingerprint(e.service(), e.level(), norm));
                  row.put("occurred_at", java.sql.Timestamp.from(e.occurredAt()));
                  return row;
                })
            .toList();

    @SuppressWarnings("unchecked")
    Map<String, Object>[] batchArray = batch.toArray(new Map[0]);
    jdbcInsert.executeBatch(batchArray);

    if (kafkaDualWrite) {
      EnvelopeProducer producer = envelopeProducer.getIfAvailable();
      if (producer == null) {
        throw new IllegalStateException(
            "Kafka dual-write enabled but EnvelopeProducer is not available.");
      }

      Instant producedAt = Instant.now();
      for (LogEntry event : entries) {
        KafkaEnvelope<LogEntry> envelope = createEnvelope(event, producedAt);
        try {
          producer
              .sendEnvelope(KafkaTopicsConfig.RAW_LOGS, event.service(), envelope)
              .get(KAFKA_SEND_DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
          meterRegistry
              .counter("ingestion.published_total", "service", event.service())
              .increment();
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          meterRegistry.counter("ingestion.publish_failures_total").increment();
          throw new IngestionQueueException("Kafka publish interrupted", ie);
        } catch (ExecutionException | TimeoutException e) {
          meterRegistry.counter("ingestion.publish_failures_total").increment();
          Throwable cause =
              e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
          throw new IngestionQueueException("Failed to queue log", cause);
        }
      }
    }
  }

  private KafkaEnvelope<LogEntry> createEnvelope(LogEntry entry, Instant producedAt) {
    String trace = traceFromMdcOrEntry(entry.traceId());

    String tenantId =
        entry.metadata() != null
                && entry.metadata().containsKey("tenantId")
                && entry.metadata().get("tenantId") != null
            ? entry.metadata().get("tenantId").toString()
            : defaultTenantId;

    Instant occurred = entry.occurredAt();
    return new KafkaEnvelope<>(
        RAW_SCHEMA, UUID.randomUUID(), occurred, producedAt, trace, tenantId, entry);
  }

  private static String traceFromMdcOrEntry(String fallbackFromEntry) {
    String fromMdc = MDC.get("traceId");
    if (fromMdc != null && !fromMdc.isEmpty()) {
      return fromMdc;
    }
    if (fallbackFromEntry != null && !fallbackFromEntry.isEmpty()) {
      return fallbackFromEntry;
    }
    return UUID.randomUUID().toString();
  }

  private String computeFingerprint(String s, String l, String m) {
    try {
      String input = s + l + m;
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Hashing failed", e);
    }
  }
}
