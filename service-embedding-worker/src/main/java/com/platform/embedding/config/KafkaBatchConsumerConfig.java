package com.platform.embedding.config;

import com.platform.queue.model.KafkaEnvelope;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Configuration
public class KafkaBatchConsumerConfig {

  @Bean
  public ConsumerFactory<String, KafkaEnvelope<?>> embedRequestConsumerFactory(
      KafkaProperties kafkaProperties,
      @Value("${embedding.consumer.max-poll-records:32}") int maxPollRecords) {
    Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
    props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.platform.*");
    props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, KafkaEnvelope.class.getName());
    props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean(name = "embedBatchKafkaListenerContainerFactory")
  public ConcurrentKafkaListenerContainerFactory<String, KafkaEnvelope<?>>
      embedBatchKafkaListenerContainerFactory(
          ConsumerFactory<String, KafkaEnvelope<?>> embedRequestConsumerFactory) {
    ConcurrentKafkaListenerContainerFactory<String, KafkaEnvelope<?>> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(embedRequestConsumerFactory);
    factory.setBatchListener(true);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
    return factory;
  }
}
