package com.platform.queue.it;

import com.platform.queue.KafkaIntegrationTestResources;
import com.platform.queue.config.KafkaTopicsConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;

@Configuration
@EnableKafka
public class TestKafkaConsumeHarness {

  @KafkaListener(
      topics = KafkaTopicsConfig.RAW_LOGS,
      groupId = "kafka-queue-it-listener",
      concurrency = "1")
  void onMessage(String value) {
    KafkaIntegrationTestResources.RECEIVED_PAYLOADS.offer(value);
  }
}
