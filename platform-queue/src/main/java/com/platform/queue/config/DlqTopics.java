package com.platform.queue.config;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;

/** DLQ topic names aligned with primary pipelines. */
public final class DlqTopics {

  public static final String RAW_LOGS_DLQ = "logs.raw.dlq";
  public static final String PREPROCESSED_LOGS_DLQ = "logs.preprocessed.dlq";
  public static final String TICKETS_NEW_DLQ = "tickets.new.dlq";

  private static final Map<String, String> SOURCE_TO_DLQ =
      Map.of(
          KafkaTopicsConfig.RAW_LOGS, RAW_LOGS_DLQ,
          KafkaTopicsConfig.PREPROCESSED_LOGS, PREPROCESSED_LOGS_DLQ,
          KafkaTopicsConfig.TICKETS_NEW, TICKETS_NEW_DLQ);

  private DlqTopics() {}

  public static String dlqTopicForSource(String sourceTopic) {
    return SOURCE_TO_DLQ.getOrDefault(sourceTopic, sourceTopic + ".dlq");
  }

  public static TopicPartition dlqPartition(ConsumerRecord<?, ?> record) {
    String dlq = dlqTopicForSource(record.topic());
    return new TopicPartition(dlq, record.partition());
  }

  public static Set<String> knownDlqNames() {
    return Set.copyOf(SOURCE_TO_DLQ.values());
  }

  /** DLQ topics for metrics / admin (stable order). */
  public static List<String> allDlqTopicNames() {
    return List.of(RAW_LOGS_DLQ, PREPROCESSED_LOGS_DLQ, TICKETS_NEW_DLQ);
  }

  public static boolean isKnownDlq(String topic) {
    return knownDlqNames().contains(topic);
  }
}
