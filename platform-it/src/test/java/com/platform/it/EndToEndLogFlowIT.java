package com.platform.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.api.ApiApplication;
import com.platform.core.model.LogIngestionRequest;
import com.platform.core.model.LogIngestionRequest.LogEntry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    classes = ApiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EndToEndLogFlowIT {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry reg) {
    reg.add("spring.datasource.url", postgres::getJdbcUrl);
    reg.add("spring.datasource.username", postgres::getUsername);
    reg.add("spring.datasource.password", postgres::getPassword);
    reg.add("spring.data.redis.host", redis::getHost);
    reg.add("spring.data.redis.port", () -> String.valueOf(redis.getMappedPort(6379)));
  }

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
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
