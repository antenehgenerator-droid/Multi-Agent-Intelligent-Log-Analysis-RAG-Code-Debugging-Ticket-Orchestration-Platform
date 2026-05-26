package com.platform.agents.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agents.config.AgentsAutoConfiguration;
import com.platform.agents.llm.PriorityLlmClient;
import com.platform.agents.llm.RootCauseLlmClient;
import com.platform.agents.llm.TicketGeneratorLlmClient;
import com.platform.agents.negotiation.NegotiationLlmClient;
import com.platform.agents.orchestrator.AgentsOrchestratorService;
import com.platform.agents.orchestrator.IncidentPipelineInput;
import com.platform.agents.orchestrator.PipelineOutcome;
import com.platform.agents.persona.PersonaLlmClient;
import com.platform.core.model.NegotiatedFinding;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@SpringBootTest(classes = {AgentsAutoConfiguration.class, EvalRunner.EvalTestConfig.class})
@Import(EvalRunner.EvalTestConfig.class)
class EvalRunner {

  @Autowired private AgentsOrchestratorService orchestrator;
  @Autowired private ResourceLoader resourceLoader;
  @Autowired private ObjectMapper mapper;

  @Test
  void runNightlyEvaluationRegressionGate() throws Exception {
    Resource datasetRes = resourceLoader.getResource("classpath:eval/golden.jsonl");

    int totalRecords = 0;
    int validLayers = 0;
    double aggregatePrecision = 0.0;

    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(datasetRes.getInputStream(), StandardCharsets.UTF_8))) {
      String currentLine;
      while ((currentLine = reader.readLine()) != null) {
        if (currentLine.isBlank()) {
          continue;
        }
        totalRecords++;
        Map<String, Object> fixture = mapper.readValue(currentLine, new TypeReference<>() {});

        NegotiatedFinding summaryOutput =
            executePipelineSimulation(fixture.get("log").toString());

        if (summaryOutput
            .primaryLayer()
            .equalsIgnoreCase(fixture.get("expected_layer").toString())) {
          validLayers++;
        }
        @SuppressWarnings("unchecked")
        List<String> expectedFiles = (List<String>) fixture.get("expected_files");
        aggregatePrecision +=
            calculatePrecisionAtFive(summaryOutput.suspectedFiles(), expectedFiles);
      }
    }

    assertThat(totalRecords).isGreaterThan(0);

    double finalLayerAccuracy = (double) validLayers / totalRecords;
    double finalRetrievalPrecision = aggregatePrecision / totalRecords;

    assertThat(finalRetrievalPrecision)
        .withFailMessage(
            "Retrieval precision dropped below 0.70 threshold target! Check parsing configs.")
        .isGreaterThanOrEqualTo(0.70);
    assertThat(finalLayerAccuracy)
        .withFailMessage("Consensus layer accuracy dropped below 0.60 target boundary!")
        .isGreaterThanOrEqualTo(0.60);
  }

  private double calculatePrecisionAtFive(List<String> suggested, List<String> golden) {
    long matchedCount = suggested.stream().limit(5).filter(golden::contains).count();
    return (double) matchedCount / 5.0;
  }

  private NegotiatedFinding executePipelineSimulation(String logMessage) {
    PipelineOutcome outcome =
        orchestrator.run(IncidentPipelineInput.forEvaluation(logMessage));
    return outcome.consensus();
  }

  @TestConfiguration
  static class EvalTestConfig {

    @Bean
    @Primary
    PersonaLlmClient evalPersonaLlmClient() {
      PersonaLlmClient client = mock(PersonaLlmClient.class);
      when(client.chat(anyString(), anyString()))
          .thenAnswer(
              invocation -> {
                String prompt = invocation.getArgument(0);
                String agentName = invocation.getArgument(1);
                String role = agentName.replace("PersonaAnalyst-", "");
                if (prompt.contains("PaymentService") || prompt.contains("NullPointerException")) {
                  return personaJson(
                      role, "NullPointerException", "APPLICATION", List.of("PaymentService.java"));
                }
                if (prompt.contains("DatabasePool") || prompt.contains("SQLException")) {
                  return personaJson(
                      role, "SQLException", "INFRASTRUCTURE", List.of("DatabasePool.java"));
                }
                return personaJson(role, "Unknown", "APPLICATION", List.of());
              });
      return client;
    }

    @Bean
    @Primary
    NegotiationLlmClient evalNegotiationLlmClient() {
      NegotiationLlmClient client = mock(NegotiationLlmClient.class);
      when(client.chat(anyString(), eq("ConsensusNegotiation")))
          .thenAnswer(
              invocation -> {
                String prompt = invocation.getArgument(0);
                if (prompt.contains("PaymentService") || prompt.contains("NullPointerException")) {
                  return negotiatedJson(
                      "NullPointerException",
                      "APPLICATION",
                      List.of("PaymentService.java"),
                      0.92);
                }
                if (prompt.contains("DatabasePool") || prompt.contains("SQLException")) {
                  return negotiatedJson(
                      "SQLException", "INFRASTRUCTURE", List.of("DatabasePool.java"), 0.91);
                }
                return negotiatedJson("REVIEW_NEEDED", "REVIEW_NEEDED", List.of(), 0.3);
              });
      return client;
    }

    @Bean
    @Primary
    RootCauseLlmClient evalRootCauseLlmClient() {
      RootCauseLlmClient client = mock(RootCauseLlmClient.class);
      when(client.chat(anyString(), eq("RootCauseAgent")))
          .thenAnswer(
              inv -> {
                String prompt = inv.getArgument(0);
                if (prompt.contains("PaymentService") || prompt.contains("NullPointerException")) {
                  return rootCauseJson(
                      "Null pointer in payment path", List.of("PaymentService.java"), 0.9);
                }
                if (prompt.contains("DatabasePool") || prompt.contains("SQLException")) {
                  return rootCauseJson(
                      "Connection pool exhausted", List.of("DatabasePool.java"), 0.9);
                }
                return rootCauseJson("Unknown", List.of(), 0.5);
              });
      return client;
    }

    @Bean
    @Primary
    TicketGeneratorLlmClient evalTicketGeneratorLlmClient() {
      TicketGeneratorLlmClient client = mock(TicketGeneratorLlmClient.class);
      when(client.chat(anyString(), eq("TicketGeneratorAgent")))
          .thenAnswer(
              inv -> {
                String prompt = inv.getArgument(0);
                if (prompt.contains("payment") || prompt.contains("Null")) {
                  return ticketJson(
                      "Payment NPE",
                      List.of("PaymentService.java"),
                      "## Root Cause Analysis\\nNPE\\n\\n## Minority Dissent Summary\\nNone\\n\\n## Suspected Files\\nPaymentService.java\\n\\n## Suggested Fix\\nGuard null");
                }
                return ticketJson(
                    "DB pool",
                    List.of("DatabasePool.java"),
                    "## Root Cause Analysis\\nPool exhausted\\n\\n## Minority Dissent Summary\\nNone\\n\\n## Suspected Files\\nDatabasePool.java\\n\\n## Suggested Fix\\nScale pool");
              });
      return client;
    }

    @Bean
    @Primary
    PriorityLlmClient evalPriorityLlmClient() {
      PriorityLlmClient client = mock(PriorityLlmClient.class);
      when(client.chat(anyString(), eq("PriorityTiebreaker"))).thenReturn("P1");
      return client;
    }

    private static String rootCauseJson(String cause, List<String> evidence, double confidence) {
      return """
          {"cause":"%s","evidence":%s,"confidence":%s,"alternatives":[]}
          """
          .formatted(cause, toJsonArray(evidence), confidence);
    }

    private static String ticketJson(String title, List<String> files, String description) {
      return """
          {"title":"%s","description":"%s","suspectedFiles":%s,"fixSuggestion":"Investigate"}
          """
          .formatted(title, description, toJsonArray(files));
    }

    private static String personaJson(
        String role, String errorType, String layer, List<String> files) {
      return """
          {"role":"%s","errorType":"%s","layer":"%s","suspectedFiles":%s,"confidence":0.90,"rationale":"eval"}
          """
          .formatted(role, errorType, layer, toJsonArray(files));
    }

    private static String negotiatedJson(
        String errorType, String layer, List<String> files, double confidence) {
      return """
          {"agreedErrorType":"%s","primaryLayer":"%s","suspectedFiles":%s,"confidence":%s,"dissent":[]}
          """
          .formatted(errorType, layer, toJsonArray(files), confidence);
    }

    private static String toJsonArray(List<String> files) {
      if (files.isEmpty()) {
        return "[]";
      }
      return "[\""
          + String.join("\",\"", files)
          + "\"]";
    }
  }
}
