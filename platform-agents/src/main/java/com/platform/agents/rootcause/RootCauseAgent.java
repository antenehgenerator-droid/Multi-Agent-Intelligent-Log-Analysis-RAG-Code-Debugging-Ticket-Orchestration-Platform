package com.platform.agents.rootcause;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agents.llm.RootCauseLlmClient;
import com.platform.agents.util.JsonPayloads;
import com.platform.core.agent.Agent;
import com.platform.core.agent.AgentContext;
import com.platform.core.model.NegotiatedFinding;
import com.platform.core.model.RootCauseHypothesis;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(RootCauseLlmClient.class)
public class RootCauseAgent implements Agent<NegotiatedFinding, RootCauseHypothesis> {

  private final RootCauseLlmClient llmClient;
  private final ObjectMapper mapper;
  private final MeterRegistry metrics;
  private final String promptTemplate;

  public RootCauseAgent(
      RootCauseLlmClient llmClient,
      ObjectMapper mapper,
      MeterRegistry metrics,
      ResourceLoader loader) {
    this.llmClient = llmClient;
    this.mapper = mapper;
    this.metrics = metrics;
    try {
      var res = loader.getResource("classpath:prompts/root_cause.v1.txt");
      this.promptTemplate = res.getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read system root cause prompt resource", e);
    }
  }

  @Override
  public String name() {
    return "RootCauseAgent";
  }

  @Override
  public RootCauseHypothesis execute(NegotiatedFinding consensus, AgentContext ctx) {
    metrics.counter("agent.invocations_total", "agent", name()).increment();

    if ("REVIEW_NEEDED".equalsIgnoreCase(consensus.agreedErrorType())) {
      return new RootCauseHypothesis(
          "Manual review required — swarm could not reach consensus",
          List.of(),
          consensus.confidence(),
          List.of());
    }

    return Timer.builder("agent.latency_seconds")
        .tag("agent", name())
        .register(metrics)
        .record(
            () -> {
              try {
                String dissentJson = mapper.writeValueAsString(consensus.dissent());
                String codeContext = ctx.getOptional("rag_code_output").map(Object::toString).orElse("");

                String compiledPrompt =
                    promptTemplate
                        .replace("{{agreedErrorType}}", consensus.agreedErrorType())
                        .replace("{{primaryLayer}}", consensus.primaryLayer())
                        .replace("{{swarmConfidence}}", String.valueOf(consensus.confidence()))
                        .replace("{{dissentJson}}", dissentJson)
                        .replace(
                            "{{codeChunks}}",
                            codeContext.isBlank() ? "No code attached" : codeContext);

                String jsonResponse = llmClient.chat(compiledPrompt, name());
                return mapper.readValue(
                    JsonPayloads.extractJsonPayload(jsonResponse), RootCauseHypothesis.class);
              } catch (Exception e) {
                metrics
                    .counter(
                        "agent.errors_total",
                        "agent",
                        name(),
                        "exception",
                        e.getClass().getSimpleName())
                    .increment();
                throw new RuntimeException("Reasoning execution runtime failure", e);
              }
            });
  }
}
