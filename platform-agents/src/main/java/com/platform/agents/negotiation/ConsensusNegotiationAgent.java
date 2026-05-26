package com.platform.agents.negotiation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agents.model.PerspectiveFinding;
import com.platform.core.agent.Agent;
import com.platform.core.agent.AgentContext;
import com.platform.core.model.NegotiatedFinding;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(NegotiationLlmClient.class)
public class ConsensusNegotiationAgent
    implements Agent<List<PerspectiveFinding>, NegotiatedFinding> {

  private static final String AGENT_NAME = "ConsensusNegotiation";
  private static final double MIN_CONFIDENCE_GATE = 0.4;

  private final NegotiationLlmClient llmClient;
  private final ObjectMapper objectMapper;
  private final String promptTemplate;

  public ConsensusNegotiationAgent(
      NegotiationLlmClient llmClient, ObjectMapper objectMapper, ResourceLoader loader) {
    this.llmClient = llmClient;
    this.objectMapper = objectMapper;
    try {
      Resource res = loader.getResource("classpath:prompts/consensus_negotiation.v1.txt");
      this.promptTemplate = res.getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load negotiation prompt schema asset", e);
    }
  }

  @Override
  public String name() {
    return "ConsensusNegotiationAgent";
  }

  @Override
  public NegotiatedFinding execute(List<PerspectiveFinding> findings, AgentContext ctx) {
    String requirement =
        ctx.getOptional("projectRequirement").map(Object::toString).orElse("");

    double maxConfidence =
        findings.stream().mapToDouble(PerspectiveFinding::confidence).max().orElse(0.0);
    boolean hasMajorityLayer = evaluateLayerDistribution(findings);

    if (maxConfidence < MIN_CONFIDENCE_GATE || !hasMajorityLayer) {
      return new NegotiatedFinding(
          "REVIEW_NEEDED",
          "REVIEW_NEEDED",
          Collections.emptyList(),
          maxConfidence,
          Collections.emptyList());
    }

    try {
      String swarmJson = objectMapper.writeValueAsString(findings);
      String compiledPrompt =
          promptTemplate
              .replace("{{projectRequirement}}", requirement)
              .replace("{{swarmPerspectivesJson}}", swarmJson);

      String rawJsonResult = llmClient.chat(compiledPrompt, AGENT_NAME);
      String json = extractJsonPayload(rawJsonResult);
      return objectMapper.readValue(json, NegotiatedFinding.class);
    } catch (Exception e) {
      throw new RuntimeException("FSM arbitration execution fault", e);
    }
  }

  private boolean evaluateLayerDistribution(List<PerspectiveFinding> findings) {
    if (findings.isEmpty()) {
      return false;
    }
    Map<String, Long> layerCounts =
        findings.stream()
            .collect(Collectors.groupingBy(PerspectiveFinding::layer, Collectors.counting()));
    long majorityThreshold = (findings.size() / 2) + 1;
    return layerCounts.values().stream().anyMatch(count -> count >= majorityThreshold);
  }

  private static String extractJsonPayload(String raw) {
    int start = raw.indexOf('{');
    int end = raw.lastIndexOf('}');
    if (start >= 0 && end > start) {
      return raw.substring(start, end + 1);
    }
    return raw;
  }
}
