package com.platform.worker;

import com.platform.rag.config.RagAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = "com.platform.worker")
@Import(RagAutoConfiguration.class)
public class EmbeddingWorkerApplication {

  public static void main(String[] args) {
    SpringApplication.run(EmbeddingWorkerApplication.class, args);
  }
}
