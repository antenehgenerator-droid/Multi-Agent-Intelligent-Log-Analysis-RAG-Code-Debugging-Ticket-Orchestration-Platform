package com.platform.orchestrator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;

@Component
public class ConsumerGracefulShutdown implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(ConsumerGracefulShutdown.class);

  private final KafkaListenerEndpointRegistry registry;
  private volatile boolean running = false;

  public ConsumerGracefulShutdown(KafkaListenerEndpointRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void start() {
    log.info("Consumer lifecycle started");
    running = true;
  }

  @Override
  public void stop() {
    log.info("Gracefully shutting down Kafka listeners...");
    registry.stop();
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}

