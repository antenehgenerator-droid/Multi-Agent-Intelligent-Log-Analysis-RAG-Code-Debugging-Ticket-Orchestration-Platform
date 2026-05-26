package com.platform.agents.config;

import com.platform.agents.negotiation.NegotiationLlmClient;
import com.platform.core.agent.TaskTier;
import com.platform.llm.gateway.LLmGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBean(LLmGateway.class)
public class NegotiationLlmConfiguration {

  @Bean
  public NegotiationLlmClient negotiationLlmClient(LLmGateway llmGateway) {
    return (prompt, agentName) -> llmGateway.chat(prompt, agentName, TaskTier.MEDIUM);
  }
}
