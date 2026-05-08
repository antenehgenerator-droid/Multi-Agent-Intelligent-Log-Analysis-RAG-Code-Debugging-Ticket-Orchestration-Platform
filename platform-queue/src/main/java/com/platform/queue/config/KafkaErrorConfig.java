package com.platform.queue.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnClass(DefaultErrorHandler.class)
public class KafkaErrorConfig {

  /**
   * Publishes failed records to topics named "{original-topic}.DLT" (Kafka convention used by
   * {@link DeadLetterPublishingRecoverer}).
   */
  /**
   * Matches Spring Boot's auto-configured {@link KafkaTemplate} bean name (not envelope producer).
   */
  public static final String STANDARD_KAFKA_TEMPLATE_BEAN_NAME = "kafkaTemplate";

  @Bean
  @ConditionalOnBean(name = STANDARD_KAFKA_TEMPLATE_BEAN_NAME)
  public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
      @Qualifier(STANDARD_KAFKA_TEMPLATE_BEAN_NAME) KafkaTemplate<?, ?> kafkaTemplate) {
    return new DeadLetterPublishingRecoverer(kafkaTemplate);
  }

  @Bean
  @ConditionalOnBean(DeadLetterPublishingRecoverer.class)
  public DefaultErrorHandler kafkaDefaultErrorHandler(
      DeadLetterPublishingRecoverer deadLetterPublishingRecoverer) {
    return new DefaultErrorHandler(deadLetterPublishingRecoverer, new FixedBackOff(1000L, 3L));
  }
}
