package com.platform.llm.cache;

import com.platform.llm.config.RedisConstants;
import com.platform.llm.model.LlmResponse;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisLlmCache {
  private final RedisTemplate<String, Object> redis;

  public RedisLlmCache(RedisTemplate<String, Object> redis) {
    this.redis = redis;
  }

  public Optional<LlmResponse> getResponse(String key) {
    return Optional.ofNullable((LlmResponse) redis.opsForValue().get(RedisConstants.NS_RESP + key));
  }

  public void putResponse(String key, LlmResponse response) {
    redis.opsForValue().set(RedisConstants.NS_RESP + key, response, Duration.ofHours(24));
  }

  public void putEmbedding(String hash, float[] embedding) {
    redis.opsForValue().set(RedisConstants.NS_EMBED + hash, embedding, Duration.ofDays(7));
  }
}
