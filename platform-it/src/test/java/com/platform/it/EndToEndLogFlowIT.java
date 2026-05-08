package com.platform.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.api.ApiApplication;
import com.platform.core.model.LogIngestionRequest;
import com.platform.core.model.LogIngestionRequest.LogEntry;
import com.platform.queue.config.KafkaTopicsConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    classes = ApiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndLogFlowIT {

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
    reg.add("spring.datasource.url", postgres::getJdbcUrl);
    reg.add("spring.datasource.username", postgres::getUsername);
    reg.add("spring.datasource.password", postgres::getPassword);
    reg.add("spring.data.redis.host", redis::getHost);
    reg.add("spring.data.redis.port", () -> String.valueOf(redis.getMappedPort(6379)));
    reg.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanLogEvents() {
    jdbcTemplate.update("DELETE FROM log_events");
  }

  @Test
  @Order(1)
  void testDualWrite() throws Exception {
    LogEntry entry =
        new LogEntry(
            "service-a", "INFO", "dual-write ping", null, null, null, Instant.now(), Map.of());
    LogIngestionRequest req = new LogIngestionRequest(List.of(entry));

    Map<String, Object> consumerProps =
        new HashMap<>(
            KafkaTestUtils.consumerProps(
                kafka.getBootstrapServers(), "platform-it-" + UUID.randomUUID(), "true"));
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
      consumer.subscribe(List.of(KafkaTopicsConfig.RAW_LOGS));

      await()
          .atMost(Duration.ofSeconds(30))
          .until(
              () -> {
                consumer.poll(Duration.ofMillis(200));
                return !consumer.subscription().isEmpty() && !consumer.assignment().isEmpty();
              });

      var response = restTemplate.postForEntity("/api/v1/logs:ingest", req, Void.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

      Integer count =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM log_events WHERE service = ?", Integer.class, "service-a");
      assertThat(count).isEqualTo(1);

      ConsumerRecord<String, String> record =
          KafkaTestUtils.getSingleRecord(
              consumer, KafkaTopicsConfig.RAW_LOGS, Duration.ofSeconds(45));
      assertThat(record.key()).isEqualTo("service-a");

      ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
      JsonNode root = mapper.readTree(record.value());
      assertThat(root.get("payload").get("service").asText()).isEqualTo("service-a");
      assertThat(root.get("schema").asText()).isEqualTo("logs.raw.v1");
    }
  }

  @Test
  @Order(2)
  void testFullIngestionFlow() {
    LogIngestionRequest req = createSampleRequest(100);
    var response = restTemplate.postForEntity("/api/v1/logs:ingest", req, Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM log_events", Integer.class);
    assertThat(count).isEqualTo(100);
  }

  private static LogIngestionRequest createSampleRequest(int n) {
    List<LogEntry> entries =
        IntStream.range(0, n)
            .mapToObj(
                i ->
                    new LogEntry(
                        "svc-" + i % 3,
                        "INFO",
                        "message " + i,
                        null,
                        null,
                        null,
                        Instant.now(),
                        Map.of()))
            .toList();
    return new LogIngestionRequest(entries);
  }
}
