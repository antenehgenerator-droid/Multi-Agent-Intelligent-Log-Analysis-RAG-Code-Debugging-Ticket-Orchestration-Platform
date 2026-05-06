CREATE INDEX log_embeddings_trgm ON log_embeddings USING gin (content gin_trgm_ops);

CREATE TABLE agent_executions (
                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  pipeline_id UUID NOT NULL,
                                  agent_name TEXT NOT NULL,
                                  tokens_in INT NOT NULL DEFAULT 0,
                                  cost_usd NUMERIC(10,6) NOT NULL DEFAULT 0,
                                  started_at TIMESTAMPTZ NOT NULL
);
