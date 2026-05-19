package com.platform.rag;

import com.platform.rag.config.RagAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(RagAutoConfiguration.class)
public class RagRetrieverTestApplication {

  public static void main(String[] args) {
    SpringApplication.run(RagRetrieverTestApplication.class, args);
  }
}
