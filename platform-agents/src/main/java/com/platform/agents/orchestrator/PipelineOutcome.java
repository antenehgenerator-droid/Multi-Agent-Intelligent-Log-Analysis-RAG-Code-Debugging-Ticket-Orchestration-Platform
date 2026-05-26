package com.platform.agents.orchestrator;

import com.platform.core.model.DraftTicket;
import com.platform.core.model.NegotiatedFinding;
import com.platform.core.model.PrioritizedTicket;
import com.platform.core.model.RootCauseHypothesis;
import com.platform.core.model.TicketResult;

public record PipelineOutcome(
    TicketResult ticketResult,
    NegotiatedFinding consensus,
    RootCauseHypothesis rootCause,
    DraftTicket draft,
    PrioritizedTicket prioritized) {}
