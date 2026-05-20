package com.platform.agents.persona;

/** Narrow LLM port for persona agents (mock-friendly in unit tests). */
@FunctionalInterface
public interface PersonaLlmClient {

  String chat(String prompt, String agentName);
}
