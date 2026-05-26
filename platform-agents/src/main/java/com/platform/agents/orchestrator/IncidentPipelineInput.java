package com.platform.agents.orchestrator;

/** Input bundle for the multi-agent incident reasoning FSM. */
public record IncidentPipelineInput(
    String projectRequirement,
    String stackTrace,
    String codeContext,
    boolean anomalyDetected,
    String service) {

  public IncidentPipelineInput {
    if (service == null || service.isBlank()) {
      service = "unknown";
    }
  }

  public IncidentPipelineInput(
      String projectRequirement, String stackTrace, String codeContext, boolean anomalyDetected) {
    this(projectRequirement, stackTrace, codeContext, anomalyDetected, "unknown");
  }

  public static IncidentPipelineInput forEvaluation(String logLine) {
    String trace = logLine;
    String codeContext = "";
    if (logLine.contains("PaymentService")) {
      codeContext = "class PaymentService { void charge() { ... } }";
    } else if (logLine.contains("DatabasePool")) {
      codeContext = "class DatabasePool { ... }";
    }
    return new IncidentPipelineInput(
        "Regression evaluation gate", trace, codeContext, true, "unknown");
  }
}
