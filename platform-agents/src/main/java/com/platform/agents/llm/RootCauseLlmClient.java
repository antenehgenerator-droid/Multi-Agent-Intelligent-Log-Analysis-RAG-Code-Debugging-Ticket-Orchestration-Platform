package com.platform.agents.llm;

@FunctionalInterface
public interface RootCauseLlmClient {

  String chat(String prompt, String agentName);
}
