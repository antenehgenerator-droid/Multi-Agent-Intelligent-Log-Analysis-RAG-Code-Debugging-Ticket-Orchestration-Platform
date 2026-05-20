package com.platform.agents.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agents.model.PerspectiveFinding;
import com.platform.agents.persona.specialized.BackendPersonaAgent;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class BackendPersonaAgentTest {

  @Test
  void executeAgent_assertsRoleTaggingAndCharterStrictness() {
    PersonaLlmClient mockClient = mock(PersonaLlmClient.class);
    String mockResponsePayload =
        """
        {"role":"Backend Engineer","errorType":"NullPointerException","layer":"DATA_ACCESS",\
        "suspectedFiles":["PaymentService.java"],"confidence":0.95,\
        "rationale":"Seeded criteria test response"}
        """;

    when(mockClient.chat(contains("Backend Engineer"), contains("PersonaAnalyst-Backend Engineer")))
        .thenReturn(mockResponsePayload);

    BackendPersonaAgent agent =
        new BackendPersonaAgent(mockClient, new DefaultResourceLoader(), new ObjectMapper());
    PerspectiveFinding result =
        agent.generatePerspective("Req", "java.lang.NullPointerException", "CodeBody");

    assertThat(result.role()).isEqualTo("Backend Engineer");
    assertThat(result.confidence()).isGreaterThan(0.9);
    assertThat(result.suspectedFiles()).contains("PaymentService.java");
  }
}
