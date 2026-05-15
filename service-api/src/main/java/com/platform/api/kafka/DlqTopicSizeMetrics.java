package com.platform.api.kafka;

import com.platform.queue.config.DlqTopics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Approximate DLQ backlog as {@code kafka.dlq.size{topic}}: sum of (latest offset − earliest offset)
 * per partition (Kafka log size proxy; not consumer-group lag when no committed group exists).
 */
@Component
@ConditionalOnBean(AdminClient.class)
public class DlqTopicSizeMetrics {

  private static final Logger log = LoggerFactory.getLogger(DlqTopicSizeMetrics.class);

  private final AdminClient adminClient;
  private final MeterRegistry meterRegistry;
  private final ConcurrentHashMap<String, AtomicLong> backlogByTopic = new ConcurrentHashMap<>();

  public DlqTopicSizeMetrics(AdminClient adminClient, MeterRegistry meterRegistry) {
    this.adminClient = adminClient;
    this.meterRegistry = meterRegistry;
  }

  @PostConstruct
  void registerGauges() {
    for (String topic : DlqTopics.allDlqTopicNames()) {
      AtomicLong holder = backlogByTopic.computeIfAbsent(topic, t -> new AtomicLong(0));
      Gauge.builder("kafka.dlq.size", holder, AtomicLong::get)
          .tag("topic", topic)
          .description("Approximate DLQ log size (end offset minus beginning offset per partition)")
          .register(meterRegistry);
    }
  }

  @Scheduled(fixedDelayString = "${com.platform.kafka.dlq.metrics.poll-interval:30s}")
  public void refreshDlqSizes() {
    for (String topic : DlqTopics.allDlqTopicNames()) {
      try {
        long size = computeApproxBacklog(topic);
        backlogByTopic.get(topic).set(size);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        log.debug("DLQ size refresh failed for {}: {}", topic, e.toString());
      }
    }
  }

  private long computeApproxBacklog(String topic)
      throws ExecutionException, InterruptedException, TimeoutException {
    var descriptions =
        adminClient.describeTopics(List.of(topic)).all().get(30, TimeUnit.SECONDS);
    var td = descriptions.get(topic);
    if (td == null) {
      return 0;
    }
    List<TopicPartition> tps =
        td.partitions().stream().map(pi -> new TopicPartition(topic, pi.partition())).toList();
    if (tps.isEmpty()) {
      return 0;
    }
    Map<TopicPartition, OffsetSpec> latestReq = new HashMap<>();
    Map<TopicPartition, OffsetSpec> earliestReq = new HashMap<>();
    for (TopicPartition tp : tps) {
      latestReq.put(tp, OffsetSpec.latest());
      earliestReq.put(tp, OffsetSpec.earliest());
    }
    Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latest =
        adminClient.listOffsets(latestReq).all().get(30, TimeUnit.SECONDS);
    Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> earliest =
        adminClient.listOffsets(earliestReq).all().get(30, TimeUnit.SECONDS);
    long sum = 0;
    for (TopicPartition tp : tps) {
      long hi = latest.get(tp).offset();
      long lo = earliest.get(tp).offset();
      sum += Math.max(0, hi - lo);
    }
    return sum;
  }
}
