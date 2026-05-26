package com.platform.agents.rootcause;

import com.platform.core.agent.Agent;
import com.platform.core.agent.AgentContext;
import com.platform.core.model.NegotiatedFinding;
import com.platform.core.model.RootCauseHypothesis;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Stub root-cause step: maps negotiated consensus into a hypothesis for downstream ticket drafting.
 * Replace with a LARGE-tier LLM agent when full reasoning is wired.
 */
@Component
public class RootCauseAgent implements Agent<NegotiatedFinding, RootCauseHypothesis> {

  @Override
  public String name() {
    return "RootCauseAgent";
  }

  @Override
  public RootCauseHypothesis execute(NegotiatedFinding consensus, AgentContext ctx) {
    if ("REVIEW_NEEDED".equalsIgnoreCase(consensus.agreedErrorType())) {
      return new RootCauseHypothesis(
          "Manual review required — swarm could not reach consensus",
          List.of(),
          consensus.confidence(),
          List.of());
    }

    String cause =
        consensus.agreedErrorType()
            + " in "
            + consensus.primaryLayer()
            + " (files: "
            + String.join(", ", consensus.suspectedFiles())
            + ")";
    return new RootCauseHypothesis(
        cause,
        consensus.suspectedFiles(),
        consensus.confidence(),
        List.of());
  }
}
