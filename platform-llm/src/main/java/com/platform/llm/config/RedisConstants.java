package com.platform.llm.config;

public final class RedisConstants {
  public static final String NS_RESP = "llm:resp:"; // Cache Chat Responses
  public static final String NS_EMBED = "llm:embed:"; // Cache Embeddings
  public static final String NS_PROMPT_TMPL = "llm:tmpl:"; // Prompt Templates
  public static final String NS_AGENT_STATE = "llm:agent:"; // Multi-Agent state
  public static final String NS_CONTEXT_WIN = "llm:ctx:"; // Context windows
  public static final String NS_TOKEN_USAGE = "llm:usage:"; // Usage metrics
  public static final String NS_RAG_IDX = "llm:idx:"; // RAG Index keys
  public static final String NS_LOCK = "llm:lock:"; // Distributed locks
}
