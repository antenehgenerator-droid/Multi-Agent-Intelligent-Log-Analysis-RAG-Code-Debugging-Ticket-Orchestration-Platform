package com.platform.worker.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.worker.EmbeddingWorkerApplication;
import com.platform.worker.model.EmbedRequest;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = EmbeddingWorkerApplication.class)
@Testcontainers
class BatchConsumerIT {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    registry.add("embedding.provider", () -> "openai");
  }

  @Autowired private BatchConsumer batchConsumer;

  @Autowired private JdbcTemplate jdbc;

  @MockBean private EmbeddingModel mockEmbeddingModel;

  @Test
  void consumeBatch_withFiftyPercentDuplicates_callsModelOnlyOnce() {
    when(mockEmbeddingModel.embedAll(any()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              List<TextSegment> segments = invocation.getArgument(0);
              List<Embedding> embeddings =
                  segments.stream()
                      .map(s -> Embedding.from(new float[1536]))
                      .toList();
              return Response.from(embeddings);
            });

    EmbedRequest requestOne =
        new EmbedRequest(
            "LOGS",
            "id-1",
            "Duplicate error log context statement",
            Collections.emptyMap());
    EmbedRequest requestTwo =
        new EmbedRequest(
            "LOGS",
            "id-2",
            "Duplicate error log context statement",
            Collections.emptyMap());

    List<ConsumerRecord<String, EmbedRequest>> records = new ArrayList<>();
    records.add(new ConsumerRecord<>("embed.requests", 0, 0L, "key-1", requestOne));
    records.add(new ConsumerRecord<>("embed.requests", 0, 1L, "key-2", requestTwo));

    Acknowledgment mockAck = org.mockito.Mockito.mock(Acknowledgment.class);

    batchConsumer.consumeBatch(records, mockAck);

    verify(mockEmbeddingModel, times(1)).embedAll(any());
    verify(mockAck, times(1)).acknowledge();

    Integer rowCount = jdbc.queryForObject("SELECT COUNT(*) FROM log_embeddings", Integer.class);
    assertThat(rowCount).isEqualTo(2);
  }
}
