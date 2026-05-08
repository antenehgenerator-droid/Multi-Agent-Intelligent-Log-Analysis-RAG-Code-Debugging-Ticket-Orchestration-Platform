package com.platform.api.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
    prefix = "com.platform.ingestion.kafka",
    name = "dual-write",
    havingValue = "true",
    matchIfMissing = true)
public class KafkaAdminConfiguration {

  @Bean(destroyMethod = "close")
  public AdminClient kafkaAdminClient(KafkaProperties kafkaProperties) {
    return AdminClient.create(kafkaProperties.buildAdminProperties(null));
  }
}
