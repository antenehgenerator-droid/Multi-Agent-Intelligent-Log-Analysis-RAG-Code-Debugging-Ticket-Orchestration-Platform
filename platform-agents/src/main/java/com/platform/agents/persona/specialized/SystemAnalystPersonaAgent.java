package com.platform.agents.persona.specialized;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agents.persona.BasePersonaAgent;
import com.platform.agents.persona.PersonaLlmClient;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class SystemAnalystPersonaAgent extends BasePersonaAgent {

  public SystemAnalystPersonaAgent(
      PersonaLlmClient llmClient, ResourceLoader loader, ObjectMapper objectMapper) {
    super("System Analyst", "system-analyst.txt", llmClient, loader, objectMapper);
  }
}
