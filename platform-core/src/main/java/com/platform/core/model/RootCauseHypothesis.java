package com.platform.core.model;

import java.util.List;

/**
 * Output of the RootCauseAgent (The heavy-reasoning model).
 */
public record RootCauseHypothesis(
    String cause,
    List<String> evidence,
    double confidence,
    List<String> alternatives
) {}
