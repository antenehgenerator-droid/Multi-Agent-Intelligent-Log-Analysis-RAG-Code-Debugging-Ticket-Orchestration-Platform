package com.platform.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.model.LogIngestionRequest.LogEntry;
import com.platform.queue.config.KafkaTopicsConfig;
import com.platform.queue.model.KafkaEnvelope;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = OrchestratorApplication.class)
@Testcontainers
class PreprocessingConsumerIT {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @Container
  static final KafkaContainer kafka =
      new KafkaContainer(
          DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
              .asCompatibleSubstituteFor("apache/kafka"));

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry reg) {
    reg.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    reg.add("spring.datasource.url", postgres::getJdbcUrl);
    reg.add("spring.datasource.username", postgres::getUsername);
    reg.add("spring.datasource.password", postgres::getPassword);
    reg.add("spring.data.redis.host", redis::getHost);
    reg.add("spring.data.redis.port", () -> String.valueOf(redis.getMappedPort(6379)));
  }

  @Autowired private KafkaTemplate<String, KafkaEnvelope<?>> envelopeKafkaTemplate;

  @Test
  void consumesRawAndPublishesPreprocessed() throws Exception {
    Instant now = Instant.now();
    LogEntry entry =
        new LogEntry(
            "service-a", "INFO", "user 7e36b7b0-7b4a-4ac0-93e0-7bd0d2d69a1d logged in", null, "t1",
            null, now, Map.of());
    KafkaEnvelope<LogEntry> envelope =
        new KafkaEnvelope<>("logs.raw.v1", UUID.randomUUID(), now, now, "t1", "default", entry);

    Map<String, Object> consumerProps =
        new HashMap<>(
            KafkaTestUtils.consumerProps(
                kafka.getBootstrapServers(), "orchestrator-it-" + UUID.randomUUID(), "true"));
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
      consumer.subscribe(List.of(KafkaTopicsConfig.PREPROCESSED_LOGS));

      envelopeKafkaTemplate.send(KafkaTopicsConfig.RAW_LOGS, "service-a", envelope).get(30_000, java.util.concurrent.TimeUnit.MILLISECONDS);

      ConsumerRecord<String, String> record =
          KafkaTestUtils.getSingleRecord(consumer, KafkaTopicsConfig.PREPROCESSED_LOGS, Duration.ofSeconds(45));

      assertThat(record.key()).isNotBlank();

      ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
      JsonNode root = mapper.readTree(record.value());
      assertThat(root.get("schema").asText()).isEqualTo("logs.preprocessed.v1");
      assertThat(root.get("payload").get("raw").get("service").asText()).isEqualTo("service-a");
      assertThat(root.get("payload").get("fingerprint").asText()).isNotBlank();
      assertThat(root.get("payload").get("template").asText()).contains("<UUID>");
    }
  }
}
