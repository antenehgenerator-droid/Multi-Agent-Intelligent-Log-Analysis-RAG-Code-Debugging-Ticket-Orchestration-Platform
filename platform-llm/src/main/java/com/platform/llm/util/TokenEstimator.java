package com.platform.llm.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Component;

@Component
public class TokenEstimator {
  private final EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();

  public int countTokens(String text, String modelName) {
    // Simple mapping; expand this logic for different model encodings
    return registry.getEncoding(EncodingType.CL100K_BASE).countTokens(text);
  }
}
