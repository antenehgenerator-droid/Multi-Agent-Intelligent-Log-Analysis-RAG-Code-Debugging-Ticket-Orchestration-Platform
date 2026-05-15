package com.platform.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.platform.queue.config.DlqTopics;
import com.platform.queue.config.KafkaTopicsConfig;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = OrchestratorApplication.class)
@Testcontainers
@EmbeddedKafka(
    partitions = 1,
    topics = {
      KafkaTopicsConfig.RAW_LOGS,
      DlqTopics.RAW_LOGS_DLQ,
      KafkaTopicsConfig.PREPROCESSED_LOGS,
      DlqTopics.PREPROCESSED_LOGS_DLQ,
      KafkaTopicsConfig.TICKETS_NEW,
      DlqTopics.TICKETS_NEW_DLQ
    })
@TestPropertySource(properties = "com.platform.kafka.error-handler.mode=fast")
class RawLogPoisonMessageDlqIT {

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

  @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;

  @Test
  void malformedJsonOnRawLogsEndsUpOnDlqWithErrorHeaders() throws Exception {
    String bootstrap = embeddedKafkaBroker.getBrokersAsString();

    Map<String, Object> producerProps = new HashMap<>(KafkaTestUtils.producerProps(bootstrap));
    producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

    try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(producerProps)) {
      producer
          .send(
              new ProducerRecord<>(
                  KafkaTopicsConfig.RAW_LOGS,
                  "poison-key",
                  "{not-valid-json".getBytes(StandardCharsets.UTF_8)))
          .get(30, TimeUnit.SECONDS);
    }

    Map<String, Object> consumerProps =
        new HashMap<>(
            KafkaTestUtils.consumerProps(bootstrap, "dlq-verify-" + UUID.randomUUID(), "true"));
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

    try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps)) {
      consumer.subscribe(List.of(DlqTopics.RAW_LOGS_DLQ));
      await()
          .atMost(Duration.ofSeconds(45))
          .pollInterval(Duration.ofMillis(300))
          .untilAsserted(
              () -> {
                ConsumerRecords<String, byte[]> polled = consumer.poll(Duration.ofMillis(900));
                assertThat(polled.isEmpty()).isFalse();
                ConsumerRecord<String, byte[]> rec = polled.iterator().next();
                assertThat(rec.value()).isNotNull();
                assertThat(new String(rec.value(), StandardCharsets.UTF_8))
                    .contains("{not-valid-json");
                boolean hasErrorClass =
                    java.util.stream.StreamSupport.stream(rec.headers().spliterator(), false)
                        .anyMatch(h -> "x-error-class".equals(h.key()));
                assertThat(hasErrorClass).isTrue();
              });
    }
  }
}
