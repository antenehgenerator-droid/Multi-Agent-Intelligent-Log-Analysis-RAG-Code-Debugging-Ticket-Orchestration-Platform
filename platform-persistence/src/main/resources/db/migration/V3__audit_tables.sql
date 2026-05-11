CREATE TABLE agent_executions (
    id UUID PRIMARY KEY,
    agent_name VARCHAR(50),
    trace_id VARCHAR(100),
    status VARCHAR(20),
    latency_ms BIGINT,
    llm_cost DECIMAL(10, 5) DEFAULT 0,
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

