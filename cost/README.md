# Cost Control and Reactive Mitigation Subsystem Architecture

The execution of LLM-backed logic adheres to a multi-tiered structural degradation loop designed to eliminate unexpected financial burst spikes during application failure events.

## Dynamic Fallback Matrix Topology

The system automatically switches execution context behavior based on the percentage of current daily spend utilized:

```
[Current Utilization Rate]
│
├── < 70%   ──> NORMAL   ──> Execute original requested model layouts
├── >= 70%  ──> DEGRADE  ──> Demote LARGE executions down to MEDIUM targets
├── >= 90%  ──> CRITICAL ──> Demote all task routing down to SMALL/CHEAP models
└── >= 100% ──> EXHAUSTED──> Drop LLM execution paths entirely; generate deterministic stubs
```

## Resilience Mitigation Design

All metrics tracking cost and token volume calculations increment independently of transactional rollbacks. This ensures that even if an execution path crashes or errors out halfway through, every consumed token is recorded, keeping the financial metrics accurate.

## Admin inspection

`GET /api/v1/admin/cost/today` returns current spend, daily cap, utilization percentage, and active degradation tier.

## Prometheus metrics

- `llm_cost_usd_total` — cumulative USD spend recorded by `BudgetEnforcer`
- `llm.calls_total`, `llm.latency_seconds`, `llm.tokens_in_total`, `llm.tokens_out_total` — gateway telemetry

Grafana dashboard: `monitoring/grafana/dashboards/cost-analytics-v1.json`
