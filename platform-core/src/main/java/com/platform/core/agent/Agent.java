package com.platform.core.agent;

/** The strict contract for all agents. I = Input type, O = Output type */
public interface Agent<I, O> {

  /**
   * @return A stable identifier for telemetry and tracing (e.g., "log-analysis-agent")
   */
  String name();

  /**
   * Executes the agent's logic synchronously. * @param input The specific input required by this
   * agent.
   *
   * @param ctx The shared blackboard context for this pipeline run.
   * @return The agent's structured output.
   */
  O execute(I input, AgentContext ctx);
}
