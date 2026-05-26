package com.platform.agents.ticket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agents.llm.TicketGeneratorLlmClient;
import com.platform.agents.util.JsonPayloads;
import com.platform.core.agent.Agent;
import com.platform.core.agent.AgentContext;
import com.platform.core.model.DraftTicket;
import com.platform.core.model.NegotiatedFinding;
import com.platform.core.model.RootCauseHypothesis;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(TicketGeneratorLlmClient.class)
public class TicketGeneratorAgent implements Agent<RootCauseHypothesis, DraftTicket> {

  private final TicketGeneratorLlmClient llmClient;
  private final ObjectMapper mapper;
  private final MeterRegistry metrics;
  private final String promptTemplate;

  public TicketGeneratorAgent(
      TicketGeneratorLlmClient llmClient,
      ObjectMapper mapper,
      MeterRegistry metrics,
      ResourceLoader loader) {
    this.llmClient = llmClient;
    this.mapper = mapper;
    this.metrics = metrics;
    try {
      var res = loader.getResource("classpath:prompts/ticket_generator.v1.txt");
      this.promptTemplate = res.getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load ticket generator prompt", e);
    }
  }

  @Override
  public String name() {
    return "TicketGeneratorAgent";
  }

  @Override
  public DraftTicket execute(RootCauseHypothesis hypothesis, AgentContext ctx) {
    metrics.counter("agent.invocations_total", "agent", name()).increment();
    NegotiatedFinding consensus = ctx.get("negotiated_consensus");

    try {
      String compiledPrompt =
          promptTemplate
              .replace("{{cause}}", hypothesis.cause())
              .replace("{{layer}}", consensus.primaryLayer())
              .replace("{{evidence}}", String.join("; ", hypothesis.evidence()))
              .replace("{{alternatives}}", String.join("; ", hypothesis.alternatives()));

      String rawJson = llmClient.chat(compiledPrompt, name());
      DraftTicket draft =
          mapper.readValue(JsonPayloads.extractJsonPayload(rawJson), DraftTicket.class);
      return new DraftTicket(
          draft.title(),
          draft.description(),
          draft.reproSteps(),
          draft.suspectedFiles().isEmpty()
              ? consensus.suspectedFiles()
              : draft.suspectedFiles(),
          draft.fixSuggestion());
    } catch (Exception e) {
      metrics
          .counter(
              "agent.errors_total", "agent", name(), "exception", e.getClass().getSimpleName())
          .increment();
      throw new RuntimeException("Ticket formatting execution failure", e);
    }
  }
}
