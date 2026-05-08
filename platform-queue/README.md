# platform-queue — Kafka partitioning

| Topic               | Partition key     | Rationale                                                                                                                           |
|---------------------|-------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `logs.raw`          | `service`         | Guarantees logs from the same service are processed in chronological order within that partition strip.                               |
| `logs.normalized`   | `fingerprint`      | Groups identical errors so downstream workers (including RAG) can aggregate or deduplicate without cross-partition merges. |

Other topics (`alerts.notifications`, incident/ticket/event topics, embeddings, RAG) use keys documented at the producer call sites.
