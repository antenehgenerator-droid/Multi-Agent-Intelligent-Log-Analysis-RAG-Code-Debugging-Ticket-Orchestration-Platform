package com.platform.worker.consumer;

import com.pgvector.PGvector;
import com.platform.rag.embedding.EmbeddingService;
import com.platform.worker.model.EmbedRequest;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class BatchConsumer {

  private final EmbeddingService embeddingService;
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;
  private final MeterRegistry metrics;

  public BatchConsumer(
      EmbeddingService embeddingService,
      NamedParameterJdbcTemplate jdbcTemplate,
      TransactionTemplate transactionTemplate,
      MeterRegistry metrics) {
    this.embeddingService = embeddingService;
    this.jdbcTemplate = jdbcTemplate;
    this.transactionTemplate = transactionTemplate;
    this.metrics = metrics;
  }

  @KafkaListener(
      topics = "embed.requests",
      groupId = "embedding-worker-group",
      containerFactory = "kafkaListenerContainerFactory")
  public void consumeBatch(List<ConsumerRecord<String, EmbedRequest>> records, Acknowledgment ack) {
    if (records == null || records.isEmpty()) {
      if (ack != null) {
        ack.acknowledge();
      }
      return;
    }

    metrics.summary("embedding.batch_size_bucket").record(records.size());

    Map<String, float[]> resolvedEmbeddings = new HashMap<>();
    List<EmbedRequest> executionsToPersist = new ArrayList<>();
    List<String> modelTextsToEmbed = new ArrayList<>();
    List<String> modelHashesToEmbed = new ArrayList<>();

    for (ConsumerRecord<String, EmbedRequest> record : records) {
      EmbedRequest request = record.value();
      if (request == null) {
        continue;
      }
      String hash = embeddingService.computeContentHash(request.text());
      executionsToPersist.add(request);

      if (resolvedEmbeddings.containsKey(hash)) {
        continue;
      }

      Optional<float[]> cachedVector = embeddingService.getCachedEmbedding(hash);
      if (cachedVector.isPresent()) {
        resolvedEmbeddings.put(hash, cachedVector.get());
      } else {
        resolvedEmbeddings.put(hash, null);
        modelTextsToEmbed.add(request.text());
        modelHashesToEmbed.add(hash);
      }
    }

    if (!modelTextsToEmbed.isEmpty()) {
      List<float[]> calculatedVectors =
          embeddingService.generateEmbeddingsBatch(modelTextsToEmbed, modelHashesToEmbed);
      for (int i = 0; i < modelHashesToEmbed.size(); i++) {
        resolvedEmbeddings.put(modelHashesToEmbed.get(i), calculatedVectors.get(i));
      }
    }

    transactionTemplate.executeWithoutResult(
        status -> {
          for (EmbedRequest req : executionsToPersist) {
            String hash = embeddingService.computeContentHash(req.text());
            float[] vector = resolvedEmbeddings.get(hash);
            persistToCorpusStore(req, hash, vector);
            metrics.counter("embedding.processed_total", "corpus", req.corpus()).increment();
          }
        });

    if (ack != null) {
      ack.acknowledge();
    }
  }

  private void persistToCorpusStore(EmbedRequest req, String hash, float[] vector) {
    PGvector pgVector = new PGvector(vector);
    switch (req.corpus().toUpperCase()) {
      case "LOGS" -> persistLog(req, hash, pgVector);
      case "CODE" -> persistCode(req, pgVector);
      case "INCIDENTS" -> persistIncident(req, pgVector);
      default ->
          throw new IllegalArgumentException("Unknown corpus identifier: " + req.corpus());
    }
  }

  private void persistLog(EmbedRequest req, String hash, PGvector embedding) {
    String sql =
        """
        INSERT INTO log_embeddings (fingerprint, content, embedding, last_seen_at)
        VALUES (:fingerprint, :content, :embedding, now())
        ON CONFLICT (fingerprint) DO UPDATE SET
          sample_count = log_embeddings.sample_count + 1,
          last_seen_at = now(),
          content = EXCLUDED.content,
          embedding = EXCLUDED.embedding
        """;
    jdbcTemplate.update(
        sql,
        new MapSqlParameterSource()
            .addValue("fingerprint", req.contentId() != null ? req.contentId() : hash)
            .addValue("content", req.text())
            .addValue("embedding", embedding));
  }

  private void persistCode(EmbedRequest req, PGvector embedding) {
    Map<String, String> meta = req.metadata();
    String sql =
        """
        INSERT INTO code_embeddings (repo, git_sha, file_path, fqn, embedding)
        VALUES (:repo, :git_sha, :file_path, :fqn, :embedding)
        ON CONFLICT (repo, git_sha, file_path, fqn) DO UPDATE SET
          embedding = EXCLUDED.embedding
        """;
    jdbcTemplate.update(
        sql,
        new MapSqlParameterSource()
            .addValue("repo", meta.get("repo"))
            .addValue("git_sha", meta.get("git_sha"))
            .addValue("file_path", meta.get("file_path"))
            .addValue("fqn", meta.get("fqn"))
            .addValue("embedding", embedding));
  }

  private void persistIncident(EmbedRequest req, PGvector embedding) {
    String sql =
        """
        INSERT INTO incident_embeddings (incident_id, content, embedding)
        VALUES (:incident_id::uuid, :content, :embedding)
        ON CONFLICT (incident_id) DO UPDATE SET
          content = EXCLUDED.content,
          embedding = EXCLUDED.embedding
        """;
    jdbcTemplate.update(
        sql,
        new MapSqlParameterSource()
            .addValue("incident_id", req.contentId())
            .addValue("content", req.text())
            .addValue("embedding", embedding));
  }
}
