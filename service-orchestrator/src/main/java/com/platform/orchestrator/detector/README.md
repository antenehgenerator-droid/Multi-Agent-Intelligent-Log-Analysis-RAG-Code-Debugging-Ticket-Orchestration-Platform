## Statistical Intelligence Signals

This module detects when a fingerprint’s frequency becomes abnormal compared to its rolling baseline.

| Signal | Weight | Threshold | Purpose |
| --- | --- | --- | --- |
| **Z-Score** | 0.6 | > 3.0 | Detects sudden bursts of existing errors. |
| **New Error** | 0.3 | mean == 0 & current > 5 | Alerts when a previously unseen/rare fingerprint starts happening. |
| **Rate Jump** | 0.1 | (planned) | Detects gradual degradation of service health. |

### Implementation notes

- **Windowing**: 5-minute buckets (Redis sorted sets keyed by `stats:freq:<windowId>`).
- **Spike detection**: \(z = (x-\mu)/\sigma\) when \(\sigma > 0\).
- **Cold start**: if baseline \(n < 30\), use a static threshold (50 events / 5 min) to avoid noisy early alerts.

