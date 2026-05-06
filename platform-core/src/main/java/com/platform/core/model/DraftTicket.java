package com.platform.core.model;

import java.util.List;

/**
 * Output of the TicketGeneratorAgent.
 * Formats the hypothesis into a human-readable draft before prioritization.
 */
public record DraftTicket(
    String title,
    String description,
    String reproSteps,
    List<String> suspectedFiles,
    String fixSuggestion
) {}
