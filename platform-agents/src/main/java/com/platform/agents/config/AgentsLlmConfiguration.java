package com.platform.agents.config;

import com.platform.agents.llm.PriorityLlmClient;
import com.platform.agents.llm.RootCauseLlmClient;
import com.platform.agents.llm.TicketGeneratorLlmClient;
import com.platform.core.agent.TaskTier;
import com.platform.llm.gateway.LLmGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBean(LLmGateway.class)
public class AgentsLlmConfiguration {

  @Bean
  public RootCauseLlmClient rootCauseLlmClient(LLmGateway llmGateway) {
    return (prompt, agentName) -> llmGateway.chat(prompt, agentName, TaskTier.LARGE);
  }

  @Bean
  public TicketGeneratorLlmClient ticketGeneratorLlmClient(LLmGateway llmGateway) {
    return (prompt, agentName) -> llmGateway.chat(prompt, agentName, TaskTier.SMALL);
  }

  @Bean
  public PriorityLlmClient priorityLlmClient(LLmGateway llmGateway) {
    return (prompt, agentName) -> llmGateway.chat(prompt, agentName, TaskTier.SMALL);
  }
}
