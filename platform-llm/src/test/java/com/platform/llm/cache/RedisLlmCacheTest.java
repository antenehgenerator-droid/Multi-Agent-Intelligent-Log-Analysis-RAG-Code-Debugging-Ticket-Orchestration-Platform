package com.platform.llm.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.llm.config.RedisConstants;
import com.platform.llm.model.LlmResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Bootstrap context via inner {@link SpringBootConfiguration}; this module has no
 * {@code @SpringBootApplication}.
 */
@SpringBootTest(classes = RedisLlmCacheTest.TestConfig.class)
@Testcontainers
class RedisLlmCacheTest {

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  @SpringBootConfiguration
  static class TestConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
      // Testcontainers will map this to the host IP and dynamic port
      return new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
      RedisTemplate<String, Object> template = new RedisTemplate<>();
      template.setConnectionFactory(connectionFactory);
      template.setKeySerializer(new StringRedisSerializer());
      template.setValueSerializer(new JdkSerializationRedisSerializer());
      return template;
    }

    @Bean
    public RedisLlmCache redisLlmCache(RedisTemplate<String, Object> redisTemplate) {
      return new RedisLlmCache(redisTemplate);
    }
  }

  @Autowired private RedisLlmCache cache;

  @Autowired private RedisTemplate<String, Object> redisTemplate;

  @Test
  void testCacheRoundTrip() {
    String key = "test-query-123";
    LlmResponse response = new LlmResponse("Hello World", 10, 5, 0.001);

    cache.putResponse(key, response);

    Optional<LlmResponse> cached = cache.getResponse(key);
    assertThat(cached).isPresent();
    assertThat(cached.get().content()).isEqualTo("Hello World");

    // Verify the Redis key actually has the correct namespace
    String fullKey = RedisConstants.NS_RESP + key;
    assertThat(redisTemplate.hasKey(fullKey)).isTrue();
  }

  @Test
  void shouldReturnEmptyForMiss() {
    Optional<LlmResponse> cached = cache.getResponse("non-existent-key");
    assertThat(cached).isEmpty();
  }
}
