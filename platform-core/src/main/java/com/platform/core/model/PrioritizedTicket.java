package com.platform.core.model;

/**
 * Output of the PriorityAgent.
 * Appends business logic (severity) to the draft.
 */
public record PrioritizedTicket(
    DraftTicket draft,
    String severity, // "P0", "P1", "P2", "P3"
    String priorityReasoning
) {}
