package com.platform.agents.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.platform.agents.config.AgentsAutoConfiguration;
import com.platform.agents.model.PerspectiveFinding;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@SpringBootTest(classes = {AgentsAutoConfiguration.class, SwarmOrchestrationIT.LlmTestConfig.class})
@Import(SwarmOrchestrationIT.LlmTestConfig.class)
class SwarmOrchestrationIT {

  @Autowired private PersonaOrchestrator orchestrator;

  @Test
  void executeSwarm_onPetClinicNpe_localizesErrorWithHighConfidence() {
    String requirement = "Ensure clinic pets are mapped to valid owner relationships.";
    String trace =
        "java.lang.NullPointerException: Cannot invoke Owner.getId() because owner is null\n"
            + "at org.springframework.samples.petclinic.owner.Pet.getOwner(Pet.java:42)";
    String mockCodeSnippets =
        "package org.springframework.samples.petclinic.owner;\n"
            + "public class Pet { public Owner getOwner() { return this.owner; } }";

    List<PerspectiveFinding> results =
        orchestrator.coordinateSwarm(requirement, trace, mockCodeSnippets);

    assertThat(results).hasSize(5);

    PerspectiveFinding backendOutput =
        results.stream()
            .filter(p -> "Backend Engineer".equals(p.role()))
            .findFirst()
            .orElseThrow();

    assertThat(backendOutput.confidence()).isGreaterThan(0.6);
    assertThat(backendOutput.errorType()).isEqualTo("NullPointerException");
  }

  @TestConfiguration
  static class LlmTestConfig {

    @Bean
    @Primary
    PersonaLlmClient personaLlmClient() {
      PersonaLlmClient client = mock(PersonaLlmClient.class);
      when(client.chat(anyString(), contains("PersonaAnalyst-")))
          .thenAnswer(
              invocation -> {
                String agentName = invocation.getArgument(1);
                String role = agentName.replace("PersonaAnalyst-", "");
                return personaJson(role);
              });
      return client;
    }

    private static String personaJson(String role) {
      return """
          {"role":"%s","errorType":"NullPointerException","layer":"APPLICATION",\
          "suspectedFiles":["Pet.java"],"confidence":0.85,"rationale":"Owner null at Pet.getOwner"}
          """
          .formatted(role);
    }
  }
}
