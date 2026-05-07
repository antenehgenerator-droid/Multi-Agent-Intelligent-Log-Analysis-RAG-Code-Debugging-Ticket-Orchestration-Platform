package com.platform.llm.actuator;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
public class RedisHealthIndicator implements HealthIndicator {
  private final RedisConnectionFactory factory;

  public RedisHealthIndicator(RedisConnectionFactory factory) {
    this.factory = factory;
  }

  @Override
  public Health health() {
    try {
      factory.getConnection().ping();
      return Health.up().withDetail("service", "Redis").build();
    } catch (Exception e) {
      return Health.down().withDetail("error", e.getMessage()).build();
    }
  }
}
