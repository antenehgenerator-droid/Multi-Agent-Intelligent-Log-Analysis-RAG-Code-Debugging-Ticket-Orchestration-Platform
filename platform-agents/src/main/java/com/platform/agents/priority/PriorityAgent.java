package com.platform.agents.priority;

import com.platform.agents.llm.PriorityLlmClient;
import com.platform.core.agent.Agent;
import com.platform.core.agent.AgentContext;
import com.platform.core.model.DraftTicket;
import com.platform.core.model.PrioritizedTicket;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(PriorityLlmClient.class)
public class PriorityAgent implements Agent<DraftTicket, PrioritizedTicket> {

  private final PriorityLlmClient llmClient;
  private final MeterRegistry metrics;
  private final Clock clock;
  private final String tiebreakerTemplate;

  public PriorityAgent(PriorityLlmClient llmClient, MeterRegistry metrics, ResourceLoader loader) {
    this(llmClient, metrics, Clock.systemDefaultZone(), loader);
  }

  PriorityAgent(
      PriorityLlmClient llmClient, MeterRegistry metrics, Clock clock, ResourceLoader loader) {
    this.llmClient = llmClient;
    this.metrics = metrics;
    this.clock = clock;
    try {
      var res = loader.getResource("classpath:prompts/priority_tiebreaker.v1.txt");
      this.tiebreakerTemplate = res.getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load priority tiebreaker prompt", e);
    }
  }

  @Override
  public String name() {
    return "PriorityAgent";
  }

  @Override
  public PrioritizedTicket execute(DraftTicket draft, AgentContext ctx) {
    metrics.counter("agent.invocations_total", "agent", name()).increment();

    int weightFrequency = 3;
    String service = ctx.getOptional("service").map(Object::toString).orElse("");
    int weightServiceTier = "payment-service".equalsIgnoreCase(service) ? 5 : 2;

    LocalTime now = LocalTime.now(clock);
    boolean isBusinessHours =
        now.isAfter(LocalTime.of(8, 0)) && now.isBefore(LocalTime.of(18, 0));
    int businessHourPenalty = isBusinessHours ? 1 : 3;

    int compositeScore = weightFrequency * weightServiceTier * businessHourPenalty;

    String severity = "P3";
    String justification = "Deterministic score metrics: " + compositeScore;

    if (compositeScore > 30) {
      severity = "P0";
    } else if (compositeScore >= 15) {
      severity = resolveTiebreakerViaLlm(draft, compositeScore);
      justification = "LLM mediated tiebreaker adjustment. Base score: " + compositeScore;
    } else if (compositeScore > 5) {
      severity = "P2";
    }

    return new PrioritizedTicket(draft, severity, "ENGINEERING_QUEUE", justification);
  }

  private String resolveTiebreakerViaLlm(DraftTicket draft, int score) {
    String tiebreakerPrompt =
        tiebreakerTemplate
            .replace("{{description}}", draft.description())
            .replace("{{score}}", String.valueOf(score));
    String choice = llmClient.chat(tiebreakerPrompt, "PriorityTiebreaker");
    return choice.contains("P1") ? "P1" : "P2";
  }
}
