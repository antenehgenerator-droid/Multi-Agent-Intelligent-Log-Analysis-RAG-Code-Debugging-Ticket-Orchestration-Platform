package com.platform.llm.model;

public record LlmRequest(String model, double temperature, String prompt) {}
