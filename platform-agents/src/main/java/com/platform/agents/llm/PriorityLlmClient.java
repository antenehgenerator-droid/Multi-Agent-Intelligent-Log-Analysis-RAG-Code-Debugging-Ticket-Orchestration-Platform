package com.platform.agents.llm;

@FunctionalInterface
public interface PriorityLlmClient {

  String chat(String prompt, String agentName);
}
