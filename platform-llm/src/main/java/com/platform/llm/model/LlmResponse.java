package com.platform.llm.model;

import java.io.Serializable;

public record LlmResponse(String content, int tokensIn, int tokensOut, double cost)
    implements Serializable {

  private static final long serialVersionUID = 1L;
}
