package com.platform.queue.config;

import com.platform.queue.producer.EnvelopeProducer;
import com.platform.queue.validation.JsonSchemaValidator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

@AutoConfiguration(
    after = {KafkaAutoConfiguration.class, KafkaEnvelopeProducerAutoConfiguration.class})
@ConditionalOnClass(KafkaTemplate.class)
@Import({EnvelopeProducer.class, JsonSchemaValidator.class})
public class QueueKafkaAutoConfiguration {}
