package com.platform.core.model;

/**
 * Output of the CodeAnalysisAgent. This directly matches the JSON Schema constrained by
 * LangChain4j.
 */
public record CodeFindings(
    String file, String method, String issue, String fix, double confidence // 0.0 to 1.0
    ) {}
