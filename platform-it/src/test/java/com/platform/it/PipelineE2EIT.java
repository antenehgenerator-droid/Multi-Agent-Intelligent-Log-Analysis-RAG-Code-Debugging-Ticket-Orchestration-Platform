package com.platform.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.platform.api.ApiApplication;
import com.platform.core.model.LogIngestionRequest;
import com.platform.core.model.LogIngestionRequest.LogEntry;
import com.platform.queue.config.KafkaTopicsConfig;
import com.platform.orchestrator.OrchestratorApplication;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
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
    classes = {ApiApplication.class, OrchestratorApplication.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PipelineE2EIT {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @Container
  static final KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry reg) {
    reg.add("spring.datasource.url", postgres::getJdbcUrl);
    reg.add("spring.datasource.username", postgres::getUsername);
    reg.add("spring.datasource.password", postgres::getPassword);
    reg.add("spring.data.redis.host", redis::getHost);
    reg.add("spring.data.redis.port", () -> String.valueOf(redis.getMappedPort(6379)));
    reg.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    reg.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
  }

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void testLogToTicketPipeline() throws Exception {
    LogIngestionRequest req = createSpikeRequest("auth-service", 50);

    Map<String, Object> consumerProps =
        new HashMap<>(
            KafkaTestUtils.consumerProps(
                kafka.getBootstrapServers(), "platform-it-" + UUID.randomUUID(), "true"));
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
      consumer.subscribe(List.of(KafkaTopicsConfig.TICKETS_NEW));

      var response = restTemplate.postForEntity("/api/v1/logs:ingest", req, Void.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                Integer tickets = jdbcTemplate.queryForObject("SELECT count(*) FROM tickets", Integer.class);
                assertThat(tickets).isNotNull();
                assertThat(tickets).isGreaterThan(0);
              });

      ConsumerRecord<String, String> record =
          KafkaTestUtils.getSingleRecord(consumer, KafkaTopicsConfig.TICKETS_NEW, Duration.ofSeconds(45));
      assertThat(record.key()).isNotBlank();
    }
  }

  private static LogIngestionRequest createSpikeRequest(String service, int n) {
    List<LogEntry> entries =
        IntStream.range(0, n)
            .mapToObj(
                i ->
                    new LogEntry(
                        service,
                        "ERROR",
                        "Connection failed to 10.0.0." + i + ":5432 after " + (1000 + i) + "ms",
                        null,
                        null,
                        null,
                        Instant.now(),
                        Map.of()))
            .toList();
    return new LogIngestionRequest(entries);
  }
}

