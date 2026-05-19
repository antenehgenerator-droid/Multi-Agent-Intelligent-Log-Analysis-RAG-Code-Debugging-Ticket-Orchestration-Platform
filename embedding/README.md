# Embedding pipeline

Week 3 architecture lives in:

- **`platform-rag`** — `EmbeddingService` (LangChain4j + Redis cache)
- **`service-embedding-worker`** — `BatchConsumer` on topic `embed.requests`

See [service-embedding-worker/src/main/resources/embedding/README.md](../service-embedding-worker/src/main/resources/embedding/README.md) for the content-hash cost lever and processing flow.
