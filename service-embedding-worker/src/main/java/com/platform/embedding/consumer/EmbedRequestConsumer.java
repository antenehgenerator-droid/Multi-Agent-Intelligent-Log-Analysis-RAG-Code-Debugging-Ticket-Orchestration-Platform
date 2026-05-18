package com.platform.embedding.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.embedding.EmbedRequest;
import com.platform.llm.embedding.ContentHasher;
import com.platform.llm.embedding.EmbeddingService;
import com.platform.persistence.embedding.EmbeddingStore;
import com.platform.queue.config.KafkaTopicsConfig;
import com.platform.queue.model.KafkaEnvelope;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class EmbedRequestConsumer {

  private static final Logger log = LoggerFactory.getLogger(EmbedRequestConsumer.class);
  private static final String TRACE_ID_KEY = "traceId";

  private final EmbeddingService embeddingService;
  private final EmbeddingStore embeddingStore;
  private final MeterRegistry metrics;
  private final ObjectMapper objectMapper;
  private final DistributionSummary batchSizeSummary;

  public EmbedRequestConsumer(
      EmbeddingService embeddingService,
      EmbeddingStore embeddingStore,
      MeterRegistry metrics,
      ObjectMapper objectMapper) {
    this.embeddingService = embeddingService;
    this.embeddingStore = embeddingStore;
    this.metrics = metrics;
    this.objectMapper = objectMapper;
    this.batchSizeSummary =
        DistributionSummary.builder("embedding.batch_size_bucket")
            .description("Embed request batch sizes observed by the worker")
            .serviceLevelObjectives(16, 24, 32)
            .register(metrics);
  }

  @KafkaListener(
      topics = KafkaTopicsConfig.EMBED_REQUESTS,
      groupId = "embedding-worker-group",
      containerFactory = "embedBatchKafkaListenerContainerFactory")
  public void onBatch(@Payload List<KafkaEnvelope<?>> envelopes, Acknowledgment ack) {
    if (envelopes == null || envelopes.isEmpty()) {
      if (ack != null) {
        ack.acknowledge();
      }
      return;
    }

    try {
      batchSizeSummary.record(envelopes.size());
      List<EmbedRequest> requests = new ArrayList<>(envelopes.size());
      for (KafkaEnvelope<?> envelope : envelopes) {
        if (envelope != null && envelope.traceId() != null) {
          MDC.put(TRACE_ID_KEY, envelope.traceId());
        }
        requests.add(coerceEmbedRequest(envelope));
      }

      Map<String, String> uniqueByContentHash = new LinkedHashMap<>();
      for (EmbedRequest request : requests) {
        String contentHash = ContentHasher.hashText(request.text());
        uniqueByContentHash.putIfAbsent(contentHash, request.text());
      }

      Map<String, float[]> embeddings =
          embeddingService.embedUniqueByContentHash(uniqueByContentHash);

      for (EmbedRequest request : requests) {
        String contentHash = ContentHasher.hashText(request.text());
        float[] vector = embeddings.get(contentHash);
        embeddingStore.upsert(request, vector);
        metrics
            .counter("embedding.processed_total", "corpus", request.corpus().name())
            .increment();
      }

      log.debug(
          "Processed embed batch: {} requests, {} unique content hashes",
          requests.size(),
          uniqueByContentHash.size());

      if (ack != null) {
        ack.acknowledge();
      }
    } finally {
      MDC.remove(TRACE_ID_KEY);
    }
  }

  private EmbedRequest coerceEmbedRequest(KafkaEnvelope<?> envelope) {
    Object payload = envelope == null ? null : envelope.payload();
    if (payload instanceof EmbedRequest request) {
      return request;
    }
    if (payload instanceof Map<?, ?> map) {
      return objectMapper.convertValue(map, EmbedRequest.class);
    }
    throw new IllegalArgumentException(
        "Unexpected embed.requests payload: "
            + (payload == null ? "null" : payload.getClass().getName()));
  }
}
