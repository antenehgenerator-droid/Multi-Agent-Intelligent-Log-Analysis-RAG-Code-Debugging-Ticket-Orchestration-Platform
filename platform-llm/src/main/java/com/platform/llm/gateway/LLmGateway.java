package com.platform.llm.gateway;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import com.platform.core.agent.TaskTier;
import com.platform.llm.budget.BudgetEnforcer;
import com.platform.llm.budget.ModelRouter;
import com.platform.llm.model.BudgetState;
import com.platform.llm.util.DeterministicFakeModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class LLmGateway {

  private final ModelRouter modelRouter;
  private final BudgetEnforcer budgetEnforcer;
  private final StringRedisTemplate redis;
  private final MeterRegistry metrics;
  private final Encoding tokenEncoder;
  private final DeterministicFakeModel stubModel = new DeterministicFakeModel();

  public LLmGateway(
      ModelRouter modelRouter,
      BudgetEnforcer budgetEnforcer,
      StringRedisTemplate redis,
      MeterRegistry metrics) {
    this.modelRouter = modelRouter;
    this.budgetEnforcer = budgetEnforcer;
    this.redis = redis;
    this.metrics = metrics;
    EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
    this.tokenEncoder = registry.getEncodingForModel(ModelType.GPT_4O);
  }

  public String chat(String prompt, String agentName) {
    return chat(prompt, agentName, TaskTier.MEDIUM);
  }

  public String chat(String prompt, String agentName, TaskTier tier) {
    String cacheKey = "llm:resp:" + sha256(prompt);

    String cachedResponse = redis.opsForValue().get(cacheKey);
    if (cachedResponse != null) {
      metrics
          .counter("llm.calls_total", "model", "routed", "agent", agentName, "cache", "hit")
          .increment();
      return cachedResponse;
    }
    metrics
        .counter("llm.calls_total", "model", "routed", "agent", agentName, "cache", "miss")
        .increment();

    int estimatedTokens = tokenEncoder.countTokens(prompt);
    double estimatedCost = budgetEnforcer.estimateCost(estimatedTokens);
    BudgetEnforcer.Decision decision = budgetEnforcer.preCheck(estimatedCost);
    BudgetState state = budgetEnforcer.getCurrentState();
    BudgetState.DegradationLevel routingLevel = routingLevel(state, decision);

    if (decision == BudgetEnforcer.Decision.DENY) {
      return stubResponse(prompt, agentName, cacheKey, routingLevel.name());
    }

    ChatLanguageModel model = modelRouter.pick(tier, routingLevel);
    String modelName = modelRouter.resolveModelName(tier, routingLevel);
    Response<AiMessage> modelResponse;
    try {
      modelResponse =
          Timer.builder("llm.latency_seconds")
              .tags("model", modelName, "agent", agentName)
              .publishPercentiles(0.5, 0.95, 0.99)
              .register(metrics)
              .recordCallable(() -> model.generate(UserMessage.from(prompt)));
    } catch (Exception e) {
      budgetEnforcer.recordActual(estimatedCost);
      throw new RuntimeException("Downstream structural model processing error", e);
    }

    int actualTokensIn = modelResponse.tokenUsage().inputTokenCount();
    int actualTokensOut = modelResponse.tokenUsage().outputTokenCount();
    double actualCost = budgetEnforcer.estimateCost(actualTokensIn + actualTokensOut);
    budgetEnforcer.recordActual(actualCost);

    metrics.counter("llm.tokens_in_total", "model", modelName).increment(actualTokensIn);
    metrics.counter("llm.tokens_out_total", "model", modelName).increment(actualTokensOut);
    metrics.gauge("llm.tokens.estimation_delta", actualTokensIn - estimatedTokens);

    String resultText = modelResponse.content().text();
    redis.opsForValue().set(cacheKey, resultText, Duration.ofHours(24));
    return resultText;
  }

  private String stubResponse(
      String prompt, String agentName, String cacheKey, String degradationTier) {
    metrics
        .counter(
            "llm.calls_total",
            "model",
            "stub",
            "agent",
            agentName,
            "degradation",
            degradationTier)
        .increment();
    String stub = stubModel.generate(UserMessage.from(prompt)).content().text();
    redis.opsForValue().set(cacheKey, stub, Duration.ofHours(24));
    return stub;
  }

  private static BudgetState.DegradationLevel routingLevel(
      BudgetState state, BudgetEnforcer.Decision decision) {
    BudgetState.DegradationLevel level = state.getLevel();
    if (decision == BudgetEnforcer.Decision.DEGRADE && level == BudgetState.DegradationLevel.NORMAL) {
      return BudgetState.DegradationLevel.DEGRADED;
    }
    return level;
  }

  private String sha256(String base) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }
}
