package com.platform.queue.config;

import java.util.List;
import java.util.stream.Stream;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

/** Bootstrap topic declarations for local development (Kafka admin auto-creation). */
@Configuration
@Profile("dev")
public final class KafkaTopicsConfig {

  public static final String RAW_LOGS = "logs.raw";
  public static final String PREPROCESSED_LOGS = "logs.preprocessed";
  public static final String NORMALIZED_LOGS = "logs.normalized";
  public static final String ALERTS = "alerts.notifications";
  public static final String INCIDENTS_EVENTS = "incidents.events";
  public static final String TICKETS_EVENTS = "tickets.events";
  public static final String TICKETS_NEW = "tickets.new";
  public static final String EMBEDDING_JOBS = "embedding.jobs";
  public static final String RAG_QUERIES = "rag.queries";

  /** All platform topic names including DLQs (for observability / admin tooling). */
  public static List<String> allTopicNames() {
    List<String> primary =
        List.of(
            RAW_LOGS,
            PREPROCESSED_LOGS,
            NORMALIZED_LOGS,
            ALERTS,
            INCIDENTS_EVENTS,
            TICKETS_EVENTS,
            TICKETS_NEW,
            EMBEDDING_JOBS,
            RAG_QUERIES);
    return Stream.concat(primary.stream(), DlqTopics.allDlqTopicNames().stream()).toList();
  }

  @Bean
  public NewTopic rawLogs() {
    return TopicBuilder.name(RAW_LOGS).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic preprocessedLogs() {
    return TopicBuilder.name(PREPROCESSED_LOGS).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic normalizedLogs() {
    return TopicBuilder.name(NORMALIZED_LOGS).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic alerts() {
    return TopicBuilder.name(ALERTS).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic incidentsEvents() {
    return TopicBuilder.name(INCIDENTS_EVENTS).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic ticketsEvents() {
    return TopicBuilder.name(TICKETS_EVENTS).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic ticketsNew() {
    return TopicBuilder.name(TICKETS_NEW).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic embeddingJobs() {
    return TopicBuilder.name(EMBEDDING_JOBS).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic ragQueries() {
    return TopicBuilder.name(RAG_QUERIES).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic rawLogsDlq() {
    return TopicBuilder.name(DlqTopics.RAW_LOGS_DLQ).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic preprocessedLogsDlq() {
    return TopicBuilder.name(DlqTopics.PREPROCESSED_LOGS_DLQ).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic ticketsNewDlq() {
    return TopicBuilder.name(DlqTopics.TICKETS_NEW_DLQ).partitions(3).replicas(1).build();
  }
}
