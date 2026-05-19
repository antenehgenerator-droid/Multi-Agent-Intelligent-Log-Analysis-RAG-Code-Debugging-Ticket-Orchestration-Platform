# High-Performance Hybrid Search & Rank Fusion Architecture

## Unified database execution patterns

This search engine runs hybrid lexical and vector operations in a single database round-trip. Instead of managing complex multi-infrastructure synchronization boundaries (such as Elasticsearch + Pinecone sync loops), we leverage Postgres extensions natively.

### Operational search configuration mechanics

- **Semantic querying:** Cosine distance (`<=>`) over HNSW-indexed `pgvector` columns captures contextual similarity.
- **Lexical querying:** Trigram word similarity (`%` / `similarity()`) captures exact identifiers, error tags, and tokens that dense vectors often smooth away.
- **Fusion:** Reciprocal Rank Fusion (RRF) merges vector and lexical rank lists in one CTE pipeline (`HybridRetriever`).

## Tuning Top-K selection tradeoffs

The pipeline defaults to **Top-K = 10**.

| Setting | Accuracy (recall) | Context cost | Latency |
| :--- | :--- | :--- | :--- |
| **Low Top-K (3–5)** | Misses edge cases or rare anomalies | Highly cost-efficient | Fast |
| **Default Top-K (10)** | Stable balance for code context delivery | Budget-friendly | Balanced |
| **High Top-K (25+)** | High recall, complex dependencies | Context dilution, higher token bills | Slower downstream parsing |

## Corpus routing

| Corpus | Table | Searchable text |
| :--- | :--- | :--- |
| `LOGS` | `log_embeddings` | `content` |
| `CODE` | `code_embeddings` | `content` (or `file_path \|\| fqn`) |
| `INCIDENTS` | `incident_embeddings` | `content` |

Filters map to real columns per corpus (allowlisted keys only — no dynamic JSON path injection).
