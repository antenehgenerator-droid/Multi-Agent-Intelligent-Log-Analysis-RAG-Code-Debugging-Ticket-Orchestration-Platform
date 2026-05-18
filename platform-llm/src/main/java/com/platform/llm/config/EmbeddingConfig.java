package com.platform.llm.config;

import com.platform.llm.embedding.FakeEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class EmbeddingConfig {

  @Bean
  public RedisTemplate<String, Object> embeddingRedisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(new JdkSerializationRedisSerializer());
    template.afterPropertiesSet();
    return template;
  }

  @Bean
  @ConditionalOnProperty(name = "embedding.provider", havingValue = "openai", matchIfMissing = true)
  public EmbeddingModel openAiEmbeddingModel(
      @Value("${embedding.openai.api-key:}") String apiKey,
      @Value("${embedding.model-name:text-embedding-3-small}") String modelName) {
    return OpenAiEmbeddingModel.builder().apiKey(apiKey).modelName(modelName).build();
  }

  @Bean
  @ConditionalOnProperty(name = "embedding.provider", havingValue = "fake")
  public EmbeddingModel fakeEmbeddingModel() {
    return new FakeEmbeddingModel();
  }
}
