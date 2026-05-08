package com.platform.api.controller;

import com.platform.queue.config.KafkaTopicsConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.Node;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@ConditionalOnBean(AdminClient.class)
public class KafkaAdminController {

  private final AdminClient adminClient;

  public KafkaAdminController(AdminClient adminClient) {
    this.adminClient = adminClient;
  }

  @GetMapping("/topics")
  public ResponseEntity<Map<String, KafkaTopicSummary>> topics()
      throws ExecutionException, InterruptedException, TimeoutException {
    Set<String> existing = adminClient.listTopics().names().get(30, TimeUnit.SECONDS);
    List<String> toDescribe = new ArrayList<>();
    for (String name : KafkaTopicsConfig.allTopicNames()) {
      if (existing.contains(name)) {
        toDescribe.add(name);
      }
    }
    if (toDescribe.isEmpty()) {
      return ResponseEntity.ok(Map.of());
    }
    DescribeTopicsResult result = adminClient.describeTopics(toDescribe);
    Map<String, TopicDescription> desc = result.all().get(30, TimeUnit.SECONDS);
    return ResponseEntity.ok(
        desc.entrySet().stream()
            .collect(
                Collectors.toMap(Map.Entry::getKey, e -> new KafkaTopicSummary(e.getValue()))));
  }

  public record KafkaTopicSummary(
      String name,
      int partitions,
      String internal,
      List<KafkaTopicSummary.PartitionSummary> splits) {

    KafkaTopicSummary(TopicDescription td) {
      this(
          td.name(),
          td.partitions().size(),
          String.valueOf(td.isInternal()),
          td.partitions().stream()
              .map(
                  p -> {
                    Node leader = p.leader();
                    int leaderId =
                        leader == null || leader.id() == Node.noNode().id() ? -1 : leader.id();
                    return new PartitionSummary(p.partition(), leaderId);
                  })
              .collect(Collectors.toList()));
    }

    public record PartitionSummary(int partition, int leaderId) {}
  }
}
