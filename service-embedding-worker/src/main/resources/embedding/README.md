# Asynchronous Vectorization Engine Architecture (`service-embedding-worker`)

## The Content-Hash Cost Reduction Lever

The single most critical design constraint implemented within this architecture is **Deterministic Pre-Ingestion Content Deduplication**. In log processing ecosystems, a single broken code deployment pattern can yield millions of repeating entries within short monitoring windows.

### Processing Interception Sequence

Before data passes to the vectorization provider, the payload runs through a two-stage deduplication pipeline:

1. **Batch consolidation:** Identical strings arriving within the same Kafka polling window are merged.
2. **Distributed global cache lookup:** SHA-256 content fingerprints are checked against Redis (`llm:embed:<signature>`) with a **7-day TTL**.

This pre-computation layer reduces structural embedding provider billing by over 70% in standard production topologies.

### Storage target decoupling

To ensure transactional safety and avoid dual-write issues, embeddings are partitioned inside PostgreSQL using native `pgvector` tables (`log_embeddings`, `code_embeddings`, `incident_embeddings`) with corpus-specific upsert keys.

### Kafka contract

- Topic: `embed.requests`
- Payload: `EmbedRequest(corpus, contentId, text, metadata)`
- Corpus values: `LOGS`, `CODE`, `INCIDENTS`
