package com.platform.orchestrator.config;

import com.platform.queue.config.DlqTopics;
import com.platform.queue.config.SequencedBackOff;
import com.platform.queue.model.KafkaEnvelope;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

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

  @Bean
  public ProducerFactory<String, byte[]> dlqProducerFactory(KafkaProperties kafkaProperties) {
    Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties(null));
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    return new DefaultKafkaProducerFactory<>(props);
  }

  @Bean
  public KafkaTemplate<String, byte[]> dlqKafkaTemplate(
      ProducerFactory<String, byte[]> dlqProducerFactory) {
    return new KafkaTemplate<>(dlqProducerFactory);
  }

  @Bean
  public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
      @Qualifier("dlqKafkaTemplate") KafkaTemplate<String, byte[]> dlqKafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            dlqKafkaTemplate, (ConsumerRecord<?, ?> record, Exception ex) -> DlqTopics.dlqPartition(record));
    recoverer.setHeadersFunction(KafkaConsumerConfig::dlqHeaders);
    return recoverer;
  }

  private static Headers dlqHeaders(ConsumerRecord<?, ?> rec, Exception ex) {
    Headers merged = new RecordHeaders();
    rec.headers().forEach(merged::add);
    merged.add("x-error-class", ex.getClass().getName().getBytes(StandardCharsets.UTF_8));
    String msg = ex.getMessage() == null ? "" : ex.getMessage();
    if (msg.length() > 900) {
      msg = msg.substring(0, 900);
    }
    merged.add("x-error-message", msg.getBytes(StandardCharsets.UTF_8));
    merged.add("x-error-original-topic", rec.topic().getBytes(StandardCharsets.UTF_8));
    merged.add(
        "x-error-original-partition",
        Integer.toString(rec.partition()).getBytes(StandardCharsets.UTF_8));
    merged.add(
        "x-error-original-offset", Long.toString(rec.offset()).getBytes(StandardCharsets.UTF_8));
    return merged;
  }

  @Bean("platformKafkaErrorHandler")
  public DefaultErrorHandler platformKafkaErrorHandler(
      DeadLetterPublishingRecoverer deadLetterPublishingRecoverer,
      @Value("${com.platform.kafka.error-handler.mode:sequenced}") String mode) {
    BackOff backOff =
        "fast".equalsIgnoreCase(mode)
            ? new FixedBackOff(10L, 2L)
            : new SequencedBackOff();
    return new DefaultErrorHandler(deadLetterPublishingRecoverer, backOff);
  }

  /**
   * Named factory to match {@code @KafkaListener(containerFactory = "kafkaListenerContainerFactory")}
   * in consumers.
   */
  @Bean(name = "kafkaListenerContainerFactory")
  public ConcurrentKafkaListenerContainerFactory<String, KafkaEnvelope<?>>
      kafkaListenerContainerFactory(
          ConsumerFactory<String, KafkaEnvelope<?>> kafkaEnvelopeConsumerFactory,
          @Qualifier("platformKafkaErrorHandler") DefaultErrorHandler platformKafkaErrorHandler) {
    ConcurrentKafkaListenerContainerFactory<String, KafkaEnvelope<?>> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(kafkaEnvelopeConsumerFactory);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
    factory.setCommonErrorHandler(platformKafkaErrorHandler);
    return factory;
  }
}
