package com.platform.agents.orchestrator;

import com.platform.agents.model.PerspectiveFinding;
import com.platform.agents.negotiation.ConsensusNegotiationAgent;
import com.platform.agents.persona.PersonaOrchestrator;
import com.platform.agents.rootcause.RootCauseAgent;
import com.platform.core.agent.AgentContext;
import com.platform.core.model.NegotiatedFinding;
import com.platform.core.model.RootCauseHypothesis;
import com.platform.core.model.TicketResult;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/**
 * Multi-agent FSM: persona swarm → consensus negotiation → root-cause hypothesis. Log, anomaly,
 * and RAG steps are supplied via {@link IncidentPipelineInput} until those agents are wired from
 * {@code service-orchestrator} / {@code platform-rag}.
 */
@Service
@ConditionalOnBean(ConsensusNegotiationAgent.class)
public class AgentsOrchestratorService {

  private final PersonaOrchestrator personaOrchestrator;
  private final ConsensusNegotiationAgent consensusNegotiationAgent;
  private final RootCauseAgent rootCauseAgent;

  public AgentsOrchestratorService(
      PersonaOrchestrator personaOrchestrator,
      ConsensusNegotiationAgent consensusNegotiationAgent,
      RootCauseAgent rootCauseAgent) {
    this.personaOrchestrator = personaOrchestrator;
    this.consensusNegotiationAgent = consensusNegotiationAgent;
    this.rootCauseAgent = rootCauseAgent;
  }

  public PipelineOutcome run(IncidentPipelineInput input) {
    AgentContext ctx = AgentContext.fresh();
    ctx.put("projectRequirement", input.projectRequirement());

    if (!input.anomalyDetected()) {
      return new PipelineOutcome(TicketResult.discarded(), null, null);
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

    List<PerspectiveFinding> perspectives = ctx.get("perspectives");

    state =
        step(
            state,
            "consensus",
            () -> consensusNegotiationAgent.execute(perspectives, ctx),
            ctx,
            "negotiated_consensus");

    NegotiatedFinding consensus = ctx.get("negotiated_consensus");

    state =
        step(
            state,
            "root",
            () -> rootCauseAgent.execute(consensus, ctx),
            ctx,
            "root_cause");

    RootCauseHypothesis rootCause = ctx.get("root_cause");

    TicketResult ticketResult =
        "REVIEW_NEEDED".equalsIgnoreCase(consensus.agreedErrorType())
            ? new TicketResult(TicketResult.Status.DEGRADED, ctx.getPipelineId(), null)
            : new TicketResult(TicketResult.Status.TICKETED, ctx.getPipelineId(), null);

    return new PipelineOutcome(ticketResult, consensus, rootCause);
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
