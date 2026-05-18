package com.platform.embedding;

import com.platform.llm.config.EmbeddingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(EmbeddingConfig.class)
@ComponentScan(basePackages = {"com.platform.embedding", "com.platform.llm.embedding", "com.platform.persistence"})
public class EmbeddingWorkerApplication {

  public static void main(String[] args) {
    SpringApplication.run(EmbeddingWorkerApplication.class, args);
  }
}
