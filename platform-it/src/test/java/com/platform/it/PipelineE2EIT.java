package com.platform.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.platform.api.ApiApplication;
import com.platform.core.model.LogIngestionRequest;
import com.platform.core.model.LogIngestionRequest.LogEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.orchestrator.OrchestratorApplication;
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

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

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

      Integer orchestratorRuns =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM agent_executions WHERE agent_name = 'ORCHESTRATOR'",
              Integer.class);
      assertThat(orchestratorRuns).isNotNull();
      assertThat(orchestratorRuns).isGreaterThan(0);

      Integer ticketedRuns =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM agent_executions WHERE agent_name = 'ORCHESTRATOR' AND status = 'TICKETED'",
              Integer.class);
      assertThat(ticketedRuns).isNotNull();
      assertThat(ticketedRuns).isGreaterThan(0);

      ConsumerRecord<String, String> record =
          KafkaTestUtils.getSingleRecord(consumer, KafkaTopicsConfig.TICKETS_NEW, Duration.ofSeconds(45));
      assertThat(record.key()).isNotBlank();
      JsonNode envelope = objectMapper.readTree(record.value());
      assertThat(envelope.get("payload").get("incidentId").asText()).isEqualTo(record.key());
    }
  }

  /**
   * Many similar ERROR lines should fingerprint together; cold-start thresholds yield a small
   * number of stub tickets (expect roughly single-digit to low tens). For live Grafana checks,
   * scrape the orchestrator and watch {@code pipeline_completed_total{terminal_state="TICKETED"}}
   * after sending ~100 logs.
   */
  @Test
  void spikeTrafficProducesBoundedStubTicketCount() {
    Integer before =
        jdbcTemplate.queryForObject("SELECT count(*) FROM tickets", Integer.class);
    assertThat(before).isNotNull();

    LogIngestionRequest req = createSpikeRequest("auth-service", 100);

    var response = restTemplate.postForEntity("/api/v1/logs:ingest", req, Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    await()
        .atMost(Duration.ofSeconds(45))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              Integer tickets = jdbcTemplate.queryForObject("SELECT count(*) FROM tickets", Integer.class);
              assertThat(tickets).isNotNull();
              int delta = tickets - before;
              assertThat(delta).isBetween(1, 25);
            });
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

