package com.platform.llm.budget;

import com.platform.llm.model.BudgetState;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class BudgetEnforcer {

  private static final double USD_PER_1K_TOKENS = 0.0001;

  private final StringRedisTemplate redis;
  private final MeterRegistry metrics;
  private final double dailyUsdLimit;

  public BudgetEnforcer(
      StringRedisTemplate redis,
      MeterRegistry metrics,
      @Value("${llm.budget.daily-usd-limit:50.00}") double dailyUsdLimit) {
    this.redis = redis;
    this.metrics = metrics;
    this.dailyUsdLimit = dailyUsdLimit;
  }

  public BudgetState getCurrentState() {
    double currentSpend = readDailySpend();
    double ratio = dailyUsdLimit > 0 ? currentSpend / dailyUsdLimit : 1.0;
    return new BudgetState(currentSpend, dailyUsdLimit, ratio);
  }

  public enum Decision {
    ALLOW,
    DEGRADE,
    DENY
  }

  public double estimateCost(int tokens) {
    return (tokens / 1000.0) * USD_PER_1K_TOKENS;
  }

  public Decision preCheck(double estimatedCost) {
    BudgetState state = getCurrentState();
    double projectedSpend = state.currentSpend() + estimatedCost;
    double projectedRatio = dailyUsdLimit > 0 ? projectedSpend / dailyUsdLimit : 1.0;

    if (projectedRatio >= 1.0) {
      return Decision.DENY;
    }
    if (projectedRatio >= 0.70) {
      return Decision.DEGRADE;
    }
    return Decision.ALLOW;
  }

  public void recordActual(double actualCost) {
    if (actualCost <= 0) {
      return;
    }
    String key = dailyBudgetKey();
    redis.opsForValue().increment(key, actualCost);
    redis.expire(key, Duration.ofDays(2));
    metrics.counter("llm_cost_usd_total").increment(actualCost);
  }

  private double readDailySpend() {
    String val = redis.opsForValue().get(dailyBudgetKey());
    return val == null ? 0.0 : Double.parseDouble(val);
  }

  private static String dailyBudgetKey() {
    return "budget:daily:" + LocalDate.now().format(DateTimeFormatter.ISO_DATE);
  }
}
