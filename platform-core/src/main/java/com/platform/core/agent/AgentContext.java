package com.platform.core.agent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class AgentContext {
  private final UUID pipelineId;
  private final Map<String, Object> blackboard;

  private AgentContext(UUID pipelineId) {
    this.pipelineId = pipelineId;
    this.blackboard = new HashMap<>();
  }

  public static AgentContext fresh() {
    return new AgentContext(UUID.randomUUID());
  }

  public UUID getPipelineId() {
    return pipelineId;
  }

  // Write-once map to prevent agents from overwriting each other's data
  public void put(String key, Object value) {
    if (blackboard.containsKey(key)) {
      throw new IllegalStateException("Key already exists in blackboard: " + key);
    }
    blackboard.put(key, value);
  }

  @SuppressWarnings("unchecked")
  public <T> T get(String key) {
    return (T) blackboard.get(key);
  }

  public <T> Optional<T> getOptional(String key) {
    return Optional.ofNullable((T) blackboard.get(key));
  }
}
