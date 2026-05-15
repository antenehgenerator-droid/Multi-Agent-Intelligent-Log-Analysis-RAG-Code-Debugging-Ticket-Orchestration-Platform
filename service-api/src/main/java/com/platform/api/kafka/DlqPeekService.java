package com.platform.api.kafka;

import com.platform.queue.config.DlqTopics;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(KafkaProperties.class)
public class DlqPeekService {

  private final KafkaProperties kafkaProperties;

  public DlqPeekService(KafkaProperties kafkaProperties) {
    this.kafkaProperties = kafkaProperties;
  }

  /**
   * Read-only tail peek of DLQ messages (newest-first bias). Intended for operators, not bulk
   * export.
   */
  public List<DlqMessageView> peek(String topic, int limit) {
    if (!DlqTopics.isKnownDlq(topic)) {
      throw new IllegalArgumentException(
          "Unknown DLQ topic; allowed: " + String.join(", ", DlqTopics.allDlqTopicNames()));
    }
    int cap = Math.min(Math.max(limit, 1), 100);
    Properties props = new Properties();
    props.putAll(kafkaProperties.buildConsumerProperties(null));
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-admin-peek-" + UUID.randomUUID());
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    List<DlqMessageView> out = new ArrayList<>();
    try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props)) {
      List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
          .map(pi -> new TopicPartition(topic, pi.partition()))
          .sorted()
          .toList();
      if (partitions.isEmpty()) {
        return out;
      }
      consumer.assign(partitions);
      Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
      int perPart = Math.max(1, (cap + partitions.size() - 1) / partitions.size());
      for (TopicPartition tp : partitions) {
        long end = endOffsets.getOrDefault(tp, 0L);
        long start = Math.max(0L, end - perPart);
        consumer.seek(tp, start);
      }
      int remaining = cap;
      Duration pollTimeout = Duration.ofMillis(800);
      while (remaining > 0) {
        ConsumerRecords<String, byte[]> records = consumer.poll(pollTimeout);
        if (records.isEmpty()) {
          break;
        }
        for (ConsumerRecord<String, byte[]> rec : records) {
          out.add(DlqMessageView.from(rec));
          if (--remaining <= 0) {
            return out;
          }
        }
      }
    }
    return out;
  }

  public record DlqMessageView(
      int partition,
      long offset,
      String key,
      String valuePreview,
      Map<String, String> headers,
      long timestamp) {

    static DlqMessageView from(ConsumerRecord<String, byte[]> rec) {
      Map<String, String> headers = new LinkedHashMap<>();
      rec.headers()
          .forEach(
              h ->
                  headers.put(
                      h.key(),
                      h.value() == null
                          ? ""
                          : new String(h.value(), StandardCharsets.UTF_8)));
      String raw =
          rec.value() == null ? "" : new String(rec.value(), StandardCharsets.UTF_8);
      String preview = raw.length() > 4096 ? raw.substring(0, 4096) + "…" : raw;
      return new DlqMessageView(
          rec.partition(),
          rec.offset(),
          rec.key(),
          preview,
          Map.copyOf(new HashMap<>(headers)),
          rec.timestamp());
    }
  }
}
