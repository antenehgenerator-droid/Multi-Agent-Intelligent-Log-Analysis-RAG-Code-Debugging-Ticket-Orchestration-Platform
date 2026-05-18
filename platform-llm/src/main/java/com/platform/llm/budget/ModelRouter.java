package com.platform.llm.budget;

import com.platform.core.agent.TaskTier;
import com.platform.llm.model.BudgetState;
import com.platform.llm.util.DeterministicFakeModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "llm.task-routing")
public class ModelRouter {

  @Value("${llm.ollama.base-url:http://localhost:11434}")
  private String baseUrl;

  private Map<String, String> mappings = Map.of();

  public void setMappings(Map<String, String> mappings) {
    this.mappings = mappings;
  }

  public String resolveModelName(TaskTier requestedTier, BudgetState.DegradationLevel degradation) {
    TaskTier effectiveTier = effectiveTier(requestedTier, degradation);
    String modelName = mappings.get(effectiveTier.name());
    if (modelName == null) {
      throw new IllegalStateException("No model mapping for tier: " + effectiveTier);
    }
    return modelName;
  }

  public ChatLanguageModel pick(TaskTier requestedTier, BudgetState.DegradationLevel degradation) {
    if (degradation == BudgetState.DegradationLevel.EXHAUSTED) {
      return new DeterministicFakeModel();
    }
    return OllamaChatModel.builder()
        .baseUrl(baseUrl)
        .modelName(resolveModelName(requestedTier, degradation))
        .temperature(0.0)
        .build();
  }

  private TaskTier effectiveTier(TaskTier requestedTier, BudgetState.DegradationLevel degradation) {
    if (degradation == BudgetState.DegradationLevel.DEGRADED && requestedTier == TaskTier.LARGE) {
      return TaskTier.MEDIUM;
    }
    if (degradation == BudgetState.DegradationLevel.CRITICAL) {
      return TaskTier.SMALL;
    }
    return requestedTier;
  }
}
