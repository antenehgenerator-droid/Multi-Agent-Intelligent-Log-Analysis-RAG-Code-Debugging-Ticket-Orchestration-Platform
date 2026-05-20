package com.platform.agents.persona;

import com.platform.agents.model.PerspectiveFinding;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/** LangChain4j AiService contract (optional binding; production path uses {@link BasePersonaAgent}). */
public interface PersonaAnalystAi {

  @UserMessage(
      """
      PROJECT REQUIREMENT: {{requirement}}
      EXCEPTION STACK TRACE: {{trace}}
      ANCHORED SOURCE CODE CONTEXT: {{code}}
      """)
  PerspectiveFinding analyze(
      @V("requirement") String requirement,
      @V("trace") String trace,
      @V("code") String code);
}
