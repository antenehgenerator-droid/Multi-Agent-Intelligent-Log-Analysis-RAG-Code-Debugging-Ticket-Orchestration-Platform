package com.platform.rag.config;

import com.platform.rag.embedding.FakeEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "com.platform.rag")
public class RagAutoConfiguration {

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
