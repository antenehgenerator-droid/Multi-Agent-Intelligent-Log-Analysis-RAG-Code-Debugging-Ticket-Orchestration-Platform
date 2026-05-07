package com.platform.llm.model;

public interface LlmGateway {
  LlmResponse chat(LlmRequest request);

  float[] embed(String text);
}
