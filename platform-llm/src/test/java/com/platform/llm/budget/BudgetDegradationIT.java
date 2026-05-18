package com.platform.llm.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.core.agent.TaskTier;
import com.platform.llm.config.LlmAutoConfiguration;
import com.platform.llm.model.BudgetState;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    classes = {
      LlmAutoConfiguration.class,
      RedisAutoConfiguration.class,
      BudgetDegradationIT.SupportConfig.class
    })
@Testcontainers
class BudgetDegradationIT {

  @Configuration
  static class SupportConfig {
    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  @Container
  static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    registry.add("llm.budget.daily-usd-limit", () -> "0.01");
    registry.add("llm.task-routing.mappings.TINY", () -> "gemma2:2b");
    registry.add("llm.task-routing.mappings.SMALL", () -> "gemma2:2b");
    registry.add("llm.task-routing.mappings.MEDIUM", () -> "gemma2:9b");
    registry.add("llm.task-routing.mappings.LARGE", () -> "gemma2:27b");
  }

  @Autowired private BudgetEnforcer enforcer;

  @Autowired private ModelRouter router;

  @Autowired private StringRedisTemplate redisTemplate;

  @Test
  void executeLargeCall_onCriticalBudget_degradesToSmallMapping() {
    String key = "budget:daily:" + LocalDate.now().format(DateTimeFormatter.ISO_DATE);
    redisTemplate.opsForValue().set(key, "0.0095");

    BudgetState state = enforcer.getCurrentState();
    assertThat(state.getLevel()).isEqualTo(BudgetState.DegradationLevel.CRITICAL);

    String resolvedModel = router.resolveModelName(TaskTier.LARGE, state.getLevel());
    assertThat(resolvedModel).isEqualTo("gemma2:2b");
  }
}
