package com.platform.rag.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

  private static final String CACHE_PREFIX = "llm:embed:";

  private final EmbeddingModel embeddingModel;
  private final StringRedisTemplate redis;
  private final MeterRegistry metrics;

  public EmbeddingService(
      EmbeddingModel embeddingModel, StringRedisTemplate redis, MeterRegistry metrics) {
    this.embeddingModel = embeddingModel;
    this.redis = redis;
    this.metrics = metrics;
  }

  public String computeContentHash(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 calculation fault", e);
    }
  }

  public Optional<float[]> getCachedEmbedding(String contentHash) {
    String rawVector = redis.opsForValue().get(CACHE_PREFIX + contentHash);
    if (rawVector != null) {
      metrics.counter("embedding.cache_hits_total").increment();
      return Optional.of(deserializeVector(rawVector));
    }
    return Optional.empty();
  }

  public List<float[]> generateEmbeddingsBatch(List<String> texts, List<String> hashes) {
    List<TextSegment> segments = texts.stream().map(TextSegment::from).toList();
    Response<List<Embedding>> response = embeddingModel.embedAll(segments);
    List<float[]> results = new ArrayList<>();

    for (int i = 0; i < response.content().size(); i++) {
      float[] vector = response.content().get(i).vector();
      results.add(vector);
      redis
          .opsForValue()
          .set(CACHE_PREFIX + hashes.get(i), serializeVector(vector), Duration.ofDays(7));
    }
    return results;
  }

  private static String serializeVector(float[] vector) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < vector.length; i++) {
      sb.append(vector[i]);
      if (i < vector.length - 1) {
        sb.append(',');
      }
    }
    return sb.toString();
  }

  private static float[] deserializeVector(String raw) {
    String[] parts = raw.split(",");
    float[] vector = new float[parts.length];
    for (int i = 0; i < parts.length; i++) {
      vector[i] = Float.parseFloat(parts[i]);
    }
    return vector;
  }
}
