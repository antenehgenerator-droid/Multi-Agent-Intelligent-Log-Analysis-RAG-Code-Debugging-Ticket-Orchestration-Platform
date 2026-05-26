package com.platform.agents.llm;

@FunctionalInterface
public interface TicketGeneratorLlmClient {

  String chat(String prompt, String agentName);
}
