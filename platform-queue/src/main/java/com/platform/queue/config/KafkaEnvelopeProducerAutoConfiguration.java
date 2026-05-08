package com.platform.queue.config;

import com.platform.queue.model.KafkaEnvelope;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/** Dedicated producer wired for typed {@link KafkaEnvelope} values via JSON serialization. */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass({KafkaTemplate.class, KafkaEnvelope.class})
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaEnvelopeProducerAutoConfiguration {

  public static final String ENVELOPE_PRODUCER_FACTORY_BEAN_NAME = "envelopeProducerFactory";

  private static ProducerFactory<String, KafkaEnvelope<?>> buildFactory(
      KafkaProperties kafkaProperties) {
    Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties(null));
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, Boolean.TRUE);
    return new DefaultKafkaProducerFactory<>(props);
  }

  @Bean(name = ENVELOPE_PRODUCER_FACTORY_BEAN_NAME)
  @ConditionalOnMissingBean(name = ENVELOPE_PRODUCER_FACTORY_BEAN_NAME)
  ProducerFactory<String, KafkaEnvelope<?>> envelopeProducerFactory(
      KafkaProperties kafkaProperties) {
    return buildFactory(kafkaProperties);
  }

  public static final String ENVELOPE_KAFKA_TEMPLATE_BEAN_NAME = "envelopeKafkaTemplate";

  @Bean(name = ENVELOPE_KAFKA_TEMPLATE_BEAN_NAME)
  @ConditionalOnMissingBean(name = ENVELOPE_KAFKA_TEMPLATE_BEAN_NAME)
  KafkaTemplate<String, KafkaEnvelope<?>> envelopeKafkaTemplate(
      @Qualifier(ENVELOPE_PRODUCER_FACTORY_BEAN_NAME)
          ProducerFactory<String, KafkaEnvelope<?>> envelopeProducerFactory) {
    return new KafkaTemplate<>(envelopeProducerFactory);
  }
}
