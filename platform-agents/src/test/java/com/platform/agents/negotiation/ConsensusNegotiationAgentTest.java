package com.platform.agents.negotiation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agents.model.PerspectiveFinding;
import com.platform.core.agent.AgentContext;
import com.platform.core.model.NegotiatedFinding;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class ConsensusNegotiationAgentTest {

  @Test
  void executeNegotiation_capturesExplicitDissentViewports() {
    NegotiationLlmClient mockClient = mock(NegotiationLlmClient.class);
    ObjectMapper mapper = new ObjectMapper();

    String simulatedConsensusPayload =
        """
        {
          "agreedErrorType": "NullPointerException",
          "primaryLayer": "DATA_ACCESS",
          "suspectedFiles": ["PaymentRepository.java"],
          "confidence": 0.88,
          "dissent": [{"role":"DevOps Engineer", "minorityView":"Looks like a DB connection timeout instead."}]
        }
        """;
    when(mockClient.chat(anyString(), anyString())).thenReturn(simulatedConsensusPayload);

    List<PerspectiveFinding> inputPerspectives =
        Arrays.asList(
            new PerspectiveFinding(
                "Backend Engineer",
                "NullPointerException",
                "DATA_ACCESS",
                Collections.emptyList(),
                0.90,
                "Trace line 12"),
            new PerspectiveFinding(
                "Full Stack Engineer",
                "NullPointerException",
                "DATA_ACCESS",
                Collections.emptyList(),
                0.85,
                "Agreed"),
            new PerspectiveFinding(
                "DevOps Engineer",
                "TimeoutException",
                "INFRASTRUCTURE",
                Collections.emptyList(),
                0.55,
                "DB lagging"));

    ConsensusNegotiationAgent agent =
        new ConsensusNegotiationAgent(mockClient, mapper, new DefaultResourceLoader());
    AgentContext context = AgentContext.fresh();
    context.put("projectRequirement", "Verify billing accounts.");

    NegotiatedFinding outcome = agent.execute(inputPerspectives, context);

    assertThat(outcome.agreedErrorType()).isEqualTo("NullPointerException");
    assertThat(outcome.dissent()).hasSize(1);
    assertThat(outcome.dissent().get(0).role()).isEqualTo("DevOps Engineer");
  }

  @Test
  void executeNegotiation_shortCircuitsWhenConfidenceTooLow() {
    NegotiationLlmClient mockClient = mock(NegotiationLlmClient.class);
    ConsensusNegotiationAgent agent =
        new ConsensusNegotiationAgent(
            mockClient, new ObjectMapper(), new DefaultResourceLoader());

    List<PerspectiveFinding> lowConfidence =
        List.of(
            new PerspectiveFinding(
                "Backend Engineer", "Unknown", "APPLICATION", List.of(), 0.2, "unclear"));

    AgentContext context = AgentContext.fresh();
    context.put("projectRequirement", "test");

    NegotiatedFinding outcome = agent.execute(lowConfidence, context);

    assertThat(outcome.agreedErrorType()).isEqualTo("REVIEW_NEEDED");
    assertThat(outcome.primaryLayer()).isEqualTo("REVIEW_NEEDED");
  }
}
