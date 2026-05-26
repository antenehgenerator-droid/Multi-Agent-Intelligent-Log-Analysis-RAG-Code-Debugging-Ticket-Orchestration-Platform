# Performance baseline (v0.3.0)

## Multi-agent swarm and conciliation strategy

Version **v0.3.0** runs five domain-specialized persona agents in parallel (CHEAP tier), then reconciles their perspectives through **ConsensusNegotiationAgent** (MID tier) before **RootCauseAgent** synthesis.

```
              ┌──> BackendPersonaAgent (CHEAP) ──┐
              ├──> DevOpsPersonaAgent (CHEAP)   ──┤
[Incident] ───┼──> FrontendPersonaAgent (CHEAP) ─┼──> [ConsensusNegotiation] ──> [RootCause]
              ├──> FullStackPersonaAgent (CHEAP) ─┤      (MID tier)
              └──> SystemAnalystAgent (CHEAP) ────┘
```

### Baseline evaluation metrics (regression gate)

| Metric | Baseline | CI threshold |
|--------|----------|--------------|
| Retrieval precision@5 | `0.742` | `>= 0.70` |
| Consensus layer accuracy | `0.685` | `>= 0.60` |
| Arbitration fault escapes | `0.021` | (monitor; forced wrong class vs `REVIEW_NEEDED`) |

Run the gate: `mvn -pl platform-agents test -Dtest=EvalRunner`

Tag: `git tag -a v0.3.0 -m "Release Milestone v0.3.0: Concurrent swarm and evaluation runner."`

---

# Performance baseline (v0.2.0)

This document captures how to reproduce load and observability baselines after DLQ, retry policy, and ingest latency metrics landed in v0.2.0.

## Workload

- **Gatling** (`platform-load-tests`): steady injection of **1000 virtual users per second** for **2 minutes** against `POST /api/v1/logs:ingest` (single event per request body).
- **Command** (API must be running and reachable):

  ```bash
  BASE_URL=http://localhost:8050 mvn -pl platform-load-tests gatling:test -Dgatling.noReports=false
  ```

## Metrics to capture during / after the run

| Signal | Prometheus / source | Notes |
|--------|----------------------|--------|
| Ingest latency | `ingestion_latency_seconds` (Timer) | Micrometer exports histogram; use Grafana or `histogram_quantile` for p50 / p95 / p99 **per environment**. |
| DLQ growth | `kafka_dlq_size{topic="logs.raw.dlq"}` (and other DLQ topics) | Gauge refreshed on a schedule (`com.platform.kafka.dlq.metrics.poll-interval`, default **30s**). Represents approximate log size (latest − earliest offset per partition), not always equal to consumer lag. |
| Consumer lag | Kafka / Grafana Kafka exporter | Use your cluster’s consumer lag panels for `log-preprocessor-group`, `anomaly-orchestrator-group`, etc. |
| CPU / RAM | Node / container metrics | e.g. `process_cpu_usage`, `jvm_memory_used_bytes`, cgroup limits. |

## Baseline run (template)

Fill this table after a controlled run (same hardware, Kafka settings, and dataset size).

| Metric | Value | Environment & date |
|--------|-------|----------------------|
| Ingest p50 (s) | _run Grafana / PromQL_ | |
| Ingest p95 (s) | | |
| Ingest p99 (s) | | |
| Peak `kafka_dlq_size` (per topic) | | |
| Max consumer lag (orchestrator preprocess) | | |
| Max consumer lag (anomaly) | | |
| API CPU % | | |
| API heap after steady state | | |

### Example PromQL (adjust job label)

```promql
histogram_quantile(0.50, sum(rate(ingestion_latency_seconds_bucket[5m])) by (le))
histogram_quantile(0.95, sum(rate(ingestion_latency_seconds_bucket[5m])) by (le))
histogram_quantile(0.99, sum(rate(ingestion_latency_seconds_bucket[5m])) by (le))
```

```promql
kafka_dlq_size
```

## Local automation note

CI runs **unit / integration** tests only; the full Gatling scenario is **not** bound to `mvn test` by default. Run `gatling:test` explicitly when you need a baseline.

## Poison / DLQ behaviour

- Malformed payloads on `logs.raw` use the orchestrator’s `DefaultErrorHandler` with **sequenced backoff** (30s → 2m → 8m → 30m → 2h) in production, then **`DeadLetterPublishingRecoverer`** to **`logs.raw.dlq`**, preserving original headers and adding `x-error-*` metadata.
- Integration test `RawLogPoisonMessageDlqIT` uses `com.platform.kafka.error-handler.mode=fast` for sub-second DLQ verification.
