package com.platform.orchestrator.config;

import com.platform.queue.model.KafkaEnvelope;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
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
public class KafkaConsumerConfig {

  @Bean
  public ConsumerFactory<String, KafkaEnvelope<?>> kafkaEnvelopeConsumerFactory(
      KafkaProperties kafkaProperties) {
    Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
    props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.platform.*");
    props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, KafkaEnvelope.class.getName());
    props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);
    return new DefaultKafkaConsumerFactory<>(props);
  }

  /**
   * Named factory to match {@code @KafkaListener(containerFactory = "kafkaListenerContainerFactory")}
   * in consumers.
   */
  @Bean(name = "kafkaListenerContainerFactory")
  public ConcurrentKafkaListenerContainerFactory<String, KafkaEnvelope<?>>
      kafkaListenerContainerFactory(
          ConsumerFactory<String, KafkaEnvelope<?>> kafkaEnvelopeConsumerFactory) {
    ConcurrentKafkaListenerContainerFactory<String, KafkaEnvelope<?>> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(kafkaEnvelopeConsumerFactory);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
    return factory;
  }
}

