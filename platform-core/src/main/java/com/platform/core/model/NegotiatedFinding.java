package com.platform.core.model;

import java.util.List;

/** Output of consensus negotiation across parallel persona perspectives. */
public record NegotiatedFinding(
    String agreedErrorType,
    String primaryLayer,
    List<String> suspectedFiles,
    double confidence,
    List<DissentingView> dissent) {

  public record DissentingView(String role, String minorityView) {}

  public NegotiatedFinding {
    suspectedFiles = suspectedFiles == null ? List.of() : List.copyOf(suspectedFiles);
    dissent = dissent == null ? List.of() : List.copyOf(dissent);
  }
}
