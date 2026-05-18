package com.platform.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.platform.core.embedding.EmbedCorpus;
import com.platform.core.embedding.EmbedRequest;
import com.platform.llm.embedding.FakeEmbeddingModel;
import com.platform.queue.config.KafkaTopicsConfig;
import com.platform.queue.model.KafkaEnvelope;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = EmbeddingWorkerApplication.class)
@Testcontainers
@ActiveProfiles("dev")
class EmbedRequestDedupIT {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @Container
  static final KafkaContainer kafka =
      new KafkaContainer(
          DockerImageName.parse("apache/kafka-native:latest")
              .asCompatibleSubstituteFor("apache/kafka"));

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry reg) {
    reg.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    reg.add("spring.datasource.url", postgres::getJdbcUrl);
    reg.add("spring.datasource.username", postgres::getUsername);
    reg.add("spring.datasource.password", postgres::getPassword);
    reg.add("spring.data.redis.host", redis::getHost);
    reg.add("spring.data.redis.port", () -> String.valueOf(redis.getMappedPort(6379)));
    reg.add("embedding.provider", () -> "fake");
  }

  @Autowired private KafkaTemplate<String, KafkaEnvelope<?>> kafkaTemplate;

  @Autowired private EmbeddingModel embeddingModel;

  @Autowired private JdbcTemplate jdbc;

  @Test
  void hundredRequestsWithHalfDuplicateTexts_invokeModelOnlyFiftyTimes() throws Exception {
    FakeEmbeddingModel fake = (FakeEmbeddingModel) embeddingModel;
    fake.reset();

    Instant now = Instant.now();
    for (int i = 0; i < 100; i++) {
      int unique = i % 50;
      String text = "steady-log-template-" + unique;
      String fingerprint = "fp-" + unique;
      EmbedRequest payload =
          new EmbedRequest(EmbedCorpus.LOG, fingerprint, text, Map.of("service", "api"));
      KafkaEnvelope<EmbedRequest> envelope =
          new KafkaEnvelope<>(
              "embed.requests.v1",
              UUID.randomUUID(),
              now,
              now,
              "trace-" + i,
              "default",
              payload);
      kafkaTemplate
          .send(KafkaTopicsConfig.EMBED_REQUESTS, fingerprint, envelope)
          .get(30, TimeUnit.SECONDS);
    }

    await()
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              assertThat(fake.getModelInvocationCount()).isEqualTo(50);
              Integer rows =
                  jdbc.queryForObject("SELECT COUNT(*) FROM log_embeddings", Integer.class);
              assertThat(rows).isEqualTo(50);
            });
  }
}
