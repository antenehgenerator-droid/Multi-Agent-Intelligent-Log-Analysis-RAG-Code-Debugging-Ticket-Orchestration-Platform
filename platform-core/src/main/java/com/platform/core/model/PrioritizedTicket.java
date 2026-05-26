package com.platform.core.model;

/** Output of the PriorityAgent. Appends business logic (severity, routing) to the draft. */
public record PrioritizedTicket(
    DraftTicket draft,
    String calculatedSeverity,
    String routingQueue,
    String scoreJustification) {}
