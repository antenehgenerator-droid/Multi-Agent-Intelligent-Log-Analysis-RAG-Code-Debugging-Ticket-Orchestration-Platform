package com.platform.agents.persona;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agents.model.PerspectiveFinding;
import com.platform.agents.persona.PersonaLlmClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

public abstract class BasePersonaAgent {

  private final String role;
  private final String promptTemplate;
  private final String charter;
  private final PersonaLlmClient llmClient;
  private final ObjectMapper objectMapper;

  protected BasePersonaAgent(
      String role,
      String charterPath,
      PersonaLlmClient llmClient,
      ResourceLoader loader,
      ObjectMapper objectMapper) {
    this.role = role;
    this.llmClient = llmClient;
    this.objectMapper = objectMapper;
    try {
      Resource promptRes = loader.getResource("classpath:prompts/persona_analysis.v1.txt");
      Resource charterRes = loader.getResource("classpath:personas/" + charterPath);
      this.promptTemplate = promptRes.getContentAsString(StandardCharsets.UTF_8);
      this.charter = charterRes.getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Failed to initialize system persona: " + role, e);
    }
  }

  public String role() {
    return role;
  }

  public PerspectiveFinding generatePerspective(
      String requirement, String trace, String codeContext) {
    String renderedSystemPrompt =
        promptTemplate.replace("{{role}}", role).replace("{{roleCharter}}", charter);

    String combinedInputMessage =
        String.format(
            "SYSTEM:\n%s\n\nUSER:\nPROJECT REQUIREMENT:\n%s\n\nSTACK TRACE:\n%s\n\nCODE:\n%s",
            renderedSystemPrompt, requirement, trace, codeContext);

    String rawJsonResult = llmClient.chat(combinedInputMessage, "PersonaAnalyst-" + role);
    return parseJsonToRecord(rawJsonResult);
  }

  private PerspectiveFinding parseJsonToRecord(String rawJson) {
    try {
      String json = extractJsonPayload(rawJson);
      return objectMapper.readValue(json, PerspectiveFinding.class);
    } catch (Exception e) {
      return new PerspectiveFinding(
          role,
          "UNKNOWN",
          "UNKNOWN",
          Collections.emptyList(),
          0.0,
          "Parsing Failure: " + e.getMessage());
    }
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
