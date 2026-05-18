package com.platform.llm.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

  private final EmbeddingModel embeddingModel;
  private final EmbeddingCache cache;
  private final MeterRegistry metrics;
  private final String modelName;

  public EmbeddingService(
      EmbeddingModel embeddingModel,
      EmbeddingCache cache,
      MeterRegistry metrics,
      @Value("${embedding.model-name:text-embedding-3-small}") String modelName) {
    this.embeddingModel = embeddingModel;
    this.cache = cache;
    this.metrics = metrics;
    this.modelName = modelName;
  }

  /**
   * Embeds unique texts only (content-hash dedup). Cache hits do not invoke the model.
   *
   * @return map of content hash → embedding vector
   */
  public Map<String, float[]> embedUniqueByContentHash(Map<String, String> contentHashToText) {
    Map<String, float[]> resolved = new LinkedHashMap<>();
    List<TextSegment> toEmbed = new ArrayList<>();
    List<String> pendingHashes = new ArrayList<>();

    for (Map.Entry<String, String> entry : contentHashToText.entrySet()) {
      String contentHash = entry.getKey();
      String text = entry.getValue();
      var cached = cache.get(text, modelName);
      if (cached.isPresent()) {
        resolved.put(contentHash, cached.get());
        metrics.counter("embedding.cache_hits_total").increment();
      } else {
        toEmbed.add(TextSegment.from(text));
        pendingHashes.add(contentHash);
      }
    }

    if (!toEmbed.isEmpty()) {
      Response<List<Embedding>> response = embeddingModel.embedAll(toEmbed);
      List<Embedding> embeddings = response.content();
      for (int i = 0; i < pendingHashes.size(); i++) {
        String contentHash = pendingHashes.get(i);
        String text = contentHashToText.get(contentHash);
        float[] vector = embeddings.get(i).vector();
        cache.put(text, modelName, vector);
        resolved.put(contentHash, vector);
      }
    }

    return resolved;
  }
}
