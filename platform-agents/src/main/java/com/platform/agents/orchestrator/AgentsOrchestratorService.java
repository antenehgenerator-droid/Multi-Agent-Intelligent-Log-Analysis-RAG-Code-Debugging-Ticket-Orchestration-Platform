package com.platform.agents.orchestrator;

import com.platform.agents.model.PerspectiveFinding;
import com.platform.agents.negotiation.ConsensusNegotiationAgent;
import com.platform.agents.persona.PersonaOrchestrator;
import com.platform.agents.priority.PriorityAgent;
import com.platform.agents.rootcause.RootCauseAgent;
import com.platform.agents.ticket.TicketGeneratorAgent;
import com.platform.core.agent.AgentContext;
import com.platform.core.model.AnomalyReport;
import com.platform.core.model.DraftTicket;
import com.platform.core.model.NegotiatedFinding;
import com.platform.core.model.ParsedLogBatch;
import com.platform.core.model.PrioritizedTicket;
import com.platform.core.model.RootCauseHypothesis;
import com.platform.core.model.TicketResult;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/**
 * Multi-agent FSM: persona swarm → consensus → root cause → ticket draft → priority scoring.
 */
@Service
@ConditionalOnBean({
  ConsensusNegotiationAgent.class,
  RootCauseAgent.class,
  TicketGeneratorAgent.class,
  PriorityAgent.class
})
public class AgentsOrchestratorService {

  private final PersonaOrchestrator personaOrchestrator;
  private final ConsensusNegotiationAgent consensusNegotiationAgent;
  private final RootCauseAgent rootCauseAgent;
  private final TicketGeneratorAgent ticketGeneratorAgent;
  private final PriorityAgent priorityAgent;
  private final MeterRegistry metrics;

  public AgentsOrchestratorService(
      PersonaOrchestrator personaOrchestrator,
      ConsensusNegotiationAgent consensusNegotiationAgent,
      RootCauseAgent rootCauseAgent,
      TicketGeneratorAgent ticketGeneratorAgent,
      PriorityAgent priorityAgent,
      MeterRegistry metrics) {
    this.personaOrchestrator = personaOrchestrator;
    this.consensusNegotiationAgent = consensusNegotiationAgent;
    this.rootCauseAgent = rootCauseAgent;
    this.ticketGeneratorAgent = ticketGeneratorAgent;
    this.priorityAgent = priorityAgent;
    this.metrics = metrics;
  }

  public PipelineOutcome run(IncidentPipelineInput input) {
    AgentContext ctx = AgentContext.fresh();
    ctx.put("projectRequirement", input.projectRequirement());
    ctx.put("rag_code_output", input.codeContext());
    ctx.put("service", input.service());

    if (!input.anomalyDetected()) {
      return emptyDiscarded();
    }

    OrchestratorState state = OrchestratorState.STARTED;

    state =
        step(
            state,
            "persona_swarm",
            () ->
                personaOrchestrator.coordinateSwarm(
                    input.projectRequirement(), input.stackTrace(), input.codeContext()),
            ctx,
            "perspectives");
    metrics.counter("agent.invocations_total", "agent", "PersonaSwarm").increment();

    List<PerspectiveFinding> perspectives = ctx.get("perspectives");

    state =
        step(
            state,
            "consensus",
            () -> consensusNegotiationAgent.execute(perspectives, ctx),
            ctx,
            "negotiated_consensus");
    metrics.counter("agent.invocations_total", "agent", "ConsensusNegotiator").increment();

    NegotiatedFinding consensus = ctx.get("negotiated_consensus");

    if ("REVIEW_NEEDED".equalsIgnoreCase(consensus.agreedErrorType())) {
      return new PipelineOutcome(
          new TicketResult(TicketResult.Status.DEGRADED, ctx.getPipelineId(), null),
          consensus,
          null,
          null,
          null);
    }

    state =
        step(
            state,
            "root",
            () -> rootCauseAgent.execute(consensus, ctx),
            ctx,
            "root_cause_hypothesis");

    RootCauseHypothesis hypothesis = ctx.get("root_cause_hypothesis");

    state =
        step(
            state,
            "ticket",
            () -> ticketGeneratorAgent.execute(hypothesis, ctx),
            ctx,
            "draft_ticket");

    DraftTicket draft = ctx.get("draft_ticket");

    state =
        step(
            state,
            "priority",
            () -> priorityAgent.execute(draft, ctx),
            ctx,
            "prioritized_ticket");

    PrioritizedTicket prioritized = ctx.get("prioritized_ticket");

    TicketResult ticketResult = TicketResult.fromPrioritized(prioritized, ctx.getPipelineId());
    return new PipelineOutcome(ticketResult, consensus, hypothesis, draft, prioritized);
  }

  /** Convenience entry for integration tests driven by {@link AnomalyReport}. */
  public PrioritizedTicket runPipelineDirectly(AnomalyReport report, String traceId) {
    ParsedLogBatch batch = report.logBatch();
    String trace =
        batch.baseStackTrace() != null && !batch.baseStackTrace().isBlank()
            ? batch.baseStackTrace()
            : traceId;
    IncidentPipelineInput input =
        new IncidentPipelineInput(
            "Incident investigation",
            trace,
            "",
            report.isAnomaly(),
            batch.service());
    return run(input).prioritized();
  }

  private static PipelineOutcome emptyDiscarded() {
    return new PipelineOutcome(TicketResult.discarded(), null, null, null, null);
  }

  private <T> OrchestratorState step(
      OrchestratorState state,
      String stepName,
      Supplier<T> action,
      AgentContext ctx,
      String blackboardKey) {
    T result = action.get();
    ctx.put(blackboardKey, result);
    return state.advance(stepName);
  }

  private enum OrchestratorState {
    STARTED,
    RUNNING;

    OrchestratorState advance(String step) {
      return RUNNING;
    }
  }
}
