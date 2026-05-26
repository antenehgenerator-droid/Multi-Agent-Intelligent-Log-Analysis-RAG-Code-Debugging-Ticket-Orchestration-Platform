package com.platform.agents.orchestrator;

import com.platform.core.model.NegotiatedFinding;
import com.platform.core.model.RootCauseHypothesis;
import com.platform.core.model.TicketResult;

public record PipelineOutcome(
    TicketResult ticketResult,
    NegotiatedFinding consensus,
    RootCauseHypothesis rootCause) {}
