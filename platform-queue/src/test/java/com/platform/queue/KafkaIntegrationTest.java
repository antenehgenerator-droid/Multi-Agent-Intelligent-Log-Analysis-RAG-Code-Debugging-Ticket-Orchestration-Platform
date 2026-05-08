package com.platform.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.queue.config.KafkaTopicsConfig;
import com.platform.queue.it.MinimalKafkaBootstrapTestApp;
import com.platform.queue.model.KafkaEnvelope;
import com.platform.queue.producer.EnvelopeProducer;
import com.platform.queue.validation.JsonSchemaValidator;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = MinimalKafkaBootstrapTestApp.class)
@Testcontainers
class KafkaIntegrationTest {

  @Container
  static final KafkaContainer KAFKA =
      new KafkaContainer(
          DockerImageName.parse("apache/kafka-native:latest")
              .asCompatibleSubstituteFor("apache/kafka"));

  @Autowired private EnvelopeProducer envelopeProducer;

  @Autowired private JsonSchemaValidator jsonSchemaValidator;

  private final ObjectMapper mapper =
      new ObjectMapper().findAndRegisterModules().registerModule(new JavaTimeModule());

  @DynamicPropertySource
  static void kafkaProps(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add("spring.kafka.producer.properties.enable.idempotence", () -> "true");
    registry.add("spring.kafka.producer.acks", () -> "all");
    registry.add(
        "spring.kafka.consumer.key-deserializer",
        () -> "org.apache.kafka.common.serialization.StringDeserializer");
    registry.add(
        "spring.kafka.consumer.value-deserializer",
        () -> "org.apache.kafka.common.serialization.StringDeserializer");
    registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
  }

  @Test
  void envelopedMessageRoundTripViaJsonUtf8WireFormat() throws Exception {
    Instant now = Instant.parse("2026-05-07T07:00:00Z");
    KafkaEnvelope<String> envelope =
        new KafkaEnvelope<>(
            "v1",
            UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380111"),
            now,
            now,
            "trace-123",
            "tenant-1",
            "Test Payload");

    envelopeProducer.send(KafkaTopicsConfig.RAW_LOGS, "key-1", envelope);

    await()
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> assertThat(KafkaIntegrationTestResources.RECEIVED_PAYLOADS).isNotEmpty());

    String raw = KafkaIntegrationTestResources.RECEIVED_PAYLOADS.take();
    jsonSchemaValidator.validate(raw);

    KafkaEnvelope<String> read =
        mapper.readValue(raw, new TypeReference<KafkaEnvelope<String>>() {});
    assertThat(read.traceId()).isEqualTo("trace-123");
    assertThat(read.tenantId()).isEqualTo("tenant-1");
    assertThat(read.payload()).isEqualTo("Test Payload");
    assertThat(read.schema()).isEqualTo("v1");
  }
}
