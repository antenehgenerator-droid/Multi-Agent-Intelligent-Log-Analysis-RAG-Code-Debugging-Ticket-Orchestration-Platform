## Statistical Intelligence Signals

This module detects when a fingerprint’s frequency becomes abnormal compared to its rolling baseline, and when a service’s aggregate error rate jumps.

| Signal | Weight | Threshold | Purpose |
| --- | --- | --- | --- |
| **Z-Score (SPIKE)** | 0.6 | z > 3.0 on per-window fingerprint count vs 24h rolling baseline | Sudden bursts of an existing fingerprint. |
| **New fingerprint (NEW_ERROR)** | 0.3 | Historical mean ≈ 0 and current window count > 5 | New or previously rare template starts occurring. |
| **Service rate jump (RATE_JUMP)** | 0.1 | Service ERROR count vs 24h baseline: z > 2.5 **or** current ≥ 1.5× mean | Gradual degradation or coordinated error storm. |

### Implementation notes

- **Windowing (5 min):** `WindowedFingerprintCounter` uses **Redis ZINCRBY** on keys `stats:freq:w:{windowId}` with member `service:fingerprint` (score = count). Per-service ERROR totals use `stats:svc:err:w:{windowId}` with member `service`. **ZRANGEBYSCORE** is exposed via `fingerprintsInCountRange(windowId, minScore, maxScore)` for volume slices within one window.
- **24h rolling baseline:** Built from the last 288 five-minute buckets (excluding the current bucket) by reading ZSET scores per window for the same `(service, fingerprint)` or `service` error series. Metadata for cold-start wall clock is stored in **Redis HASH** `stats:baseline:v1:{service}:{fingerprint}` (`BaselineStore`).
- **Spike:** \(z = (x-\mu)/\sigma\) when \(\sigma > 0\) and \(n \ge 30\) past windows.
- **Cold start (first 24h per service+fingerprint):** Fixed thresholds only — fingerprint spike ≥ 50 / 5 min, new fingerprint > 5, service ERROR count ≥ 40 / 5 min — so z-score and rate statistics are not trusted until enough wall-clock history exists.

### Metrics (Prometheus)

- `anomaly.detected_total{type, severity}` — counter on each detection (`severity` = WARNING vs CRITICAL from score).
- `anomaly.score` — `DistributionSummary` with **percentile histogram** enabled → Prometheus exposes `anomaly_score_bucket{le="..."}` style series for score magnitudes.

### Postgres MV (optional)

Rolling aggregates can alternatively be maintained as a materialized view; this deployment uses **Redis** for hot-path latency.
