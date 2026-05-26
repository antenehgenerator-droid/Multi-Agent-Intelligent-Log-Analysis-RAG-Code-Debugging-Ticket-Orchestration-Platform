package com.platform.agents.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agents.config.AgentsAutoConfiguration;
import com.platform.agents.llm.PriorityLlmClient;
import com.platform.agents.llm.RootCauseLlmClient;
import com.platform.agents.llm.TicketGeneratorLlmClient;
import com.platform.agents.negotiation.NegotiationLlmClient;
import com.platform.agents.persona.PersonaLlmClient;
import com.platform.core.model.PrioritizedTicket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@SpringBootTest(classes = {AgentsAutoConfiguration.class, EndToEndPipelineIT.PipelineTestConfig.class})
@Import(EndToEndPipelineIT.PipelineTestConfig.class)
class EndToEndPipelineIT {

  @Autowired private AgentsOrchestratorService orchestrator;

  @Test
  void executePipeline_withPetClinicNpe_producesP0TicketWithDissentContext() {
    String trace =
        "java.lang.NullPointerException: Cannot invoke Owner.getId() because owner is null\n"
            + "at org.springframework.samples.petclinic.owner.Pet.getOwner(Pet.java:42)";

    IncidentPipelineInput input =
        new IncidentPipelineInput(
            "Ensure clinic pets are mapped to valid owner relationships.",
            trace,
            "package org.springframework.samples.petclinic.owner;\n"
                + "public class Pet { public Owner getOwner() { return this.owner; } }",
            true,
            "payment-service");

    PrioritizedTicket result = orchestrator.run(input).prioritized();

    assertThat(result).isNotNull();
    assertThat(result.calculatedSeverity()).isIn("P0", "P1");
    assertThat(result.draft().suspectedFiles()).contains("Pet.java");
    assertThat(result.draft().description())
        .contains("Root Cause Analysis")
        .contains("Minority Dissent Summary");
  }

  @TestConfiguration
  static class PipelineTestConfig {

    @Bean
    @Primary
    PersonaLlmClient evalPersonaLlmClient() {
      PersonaLlmClient client = mock(PersonaLlmClient.class);
      when(client.chat(anyString(), anyString()))
          .thenAnswer(
              inv -> {
                String role = inv.getArgument(1).toString().replace("PersonaAnalyst-", "");
                return """
                    {"role":"%s","errorType":"NullPointerException","layer":"APPLICATION",\
                    "suspectedFiles":["Pet.java"],"confidence":0.90,"rationale":"owner null"}
                    """
                    .formatted(role);
              });
      return client;
    }

    @Bean
    @Primary
    NegotiationLlmClient evalNegotiationLlmClient() {
      NegotiationLlmClient client = mock(NegotiationLlmClient.class);
      when(client.chat(anyString(), eq("ConsensusNegotiation")))
          .thenReturn(
              """
              {"agreedErrorType":"NullPointerException","primaryLayer":"APPLICATION",\
              "suspectedFiles":["Pet.java"],"confidence":0.88,\
              "dissent":[{"role":"DevOps Engineer","minorityView":"Could be infra DNS lag"}]}
              """);
      return client;
    }

    @Bean
    @Primary
    RootCauseLlmClient evalRootCauseLlmClient() {
      RootCauseLlmClient client = mock(RootCauseLlmClient.class);
      when(client.chat(anyString(), eq("RootCauseAgent")))
          .thenReturn(
              """
              {"cause":"Owner reference was null when Pet.getOwner was invoked",\
              "evidence":["Pet.java:42","owner field unset"],"confidence":0.91,\
              "alternatives":["Transient infra DNS lag per DevOps dissent"]}
              """);
      return client;
    }

    @Bean
    @Primary
    TicketGeneratorLlmClient evalTicketGeneratorLlmClient() {
      TicketGeneratorLlmClient client = mock(TicketGeneratorLlmClient.class);
      when(client.chat(anyString(), eq("TicketGeneratorAgent")))
          .thenReturn(
              """
              {"title":"NPE in Pet.getOwner","description":"## Root Cause Analysis\\nOwner was null.\\n\\n## Minority Dissent Summary\\nDevOps noted possible infra lag.\\n\\n## Suspected Files\\nPet.java\\n\\n## Suggested Fix\\nValidate owner before getOwner.","suspectedFiles":["Pet.java"],"fixSuggestion":"Add null guard"}
              """);
      return client;
    }

    @Bean
    @Primary
    PriorityLlmClient evalPriorityLlmClient() {
      PriorityLlmClient client = mock(PriorityLlmClient.class);
      when(client.chat(anyString(), eq("PriorityTiebreaker"))).thenReturn("P1");
      return client;
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper().findAndRegisterModules();
    }
  }
}
