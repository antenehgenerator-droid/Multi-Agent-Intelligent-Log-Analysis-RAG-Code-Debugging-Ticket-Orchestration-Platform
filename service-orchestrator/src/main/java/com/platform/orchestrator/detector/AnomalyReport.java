package com.platform.orchestrator.detector;

import java.util.Map;

public record AnomalyReport(Type type, double score, Map<String, Object> evidence) {
  public enum Type {
    SPIKE,
    NEW_ERROR,
    RATE_JUMP
  }
}

