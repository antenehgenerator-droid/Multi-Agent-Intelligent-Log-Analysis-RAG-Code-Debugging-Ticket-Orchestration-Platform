package com.platform.llm.model;

public record BudgetState(double currentSpend, double maxLimit, double utilizationRatio) {

  public enum DegradationLevel {
    NORMAL,
    DEGRADED,
    CRITICAL,
    EXHAUSTED
  }

  public DegradationLevel getLevel() {
    if (utilizationRatio < 0.70) {
      return DegradationLevel.NORMAL;
    }
    if (utilizationRatio < 0.90) {
      return DegradationLevel.DEGRADED;
    }
    if (utilizationRatio < 1.00) {
      return DegradationLevel.CRITICAL;
    }
    return DegradationLevel.EXHAUSTED;
  }
}
