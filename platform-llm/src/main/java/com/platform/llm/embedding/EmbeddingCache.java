package com.platform.llm.embedding;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingCache {

  private static final String KEY_PREFIX = "llm:embed:";
  private static final Duration TTL = Duration.ofDays(7);

  private final RedisTemplate<String, Object> redis;

  public EmbeddingCache(RedisTemplate<String, Object> redis) {
    this.redis = redis;
  }

  public Optional<float[]> get(String text, String modelName) {
    String key = KEY_PREFIX + ContentHasher.cacheKey(text, modelName);
    Object value = redis.opsForValue().get(key);
    if (value instanceof float[] floats) {
      return Optional.of(floats);
    }
    return Optional.empty();
  }

  public void put(String text, String modelName, float[] embedding) {
    String key = KEY_PREFIX + ContentHasher.cacheKey(text, modelName);
    redis.opsForValue().set(key, embedding, TTL);
  }
}
