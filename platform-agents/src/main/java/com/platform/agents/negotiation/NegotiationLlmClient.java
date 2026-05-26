package com.platform.agents.negotiation;

/** Mock-friendly LLM port for consensus negotiation (MID-tier routing). */
@FunctionalInterface
public interface NegotiationLlmClient {

  String chat(String prompt, String agentName);
}
