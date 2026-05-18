# Embedding worker architecture

Async embedding ingestion runs in **`service-embedding-worker`**, a separate Spring Boot deployable with its own Docker image. Requests arrive on Kafka topic **`embed.requests`** as `KafkaEnvelope<EmbedRequest>` payloads.

## Most important cost lever: content-hash deduplication

**Deduplicate by content hash before calling the embedding model.** Duplicate log templates, code chunks, or incident summaries must not trigger duplicate OpenAI (or other provider) calls.

The worker builds a batch (up to 32 records per poll), then:

1. Computes `SHA-256(text)` for each request.
2. Collapses the batch to **unique content hashes** (`putIfAbsent` on hash → text).
3. Checks **Redis `EmbeddingCache`** (`hash(text + model)`, TTL **7 days**).
4. Calls `EmbeddingModel.embedAll(...)` **only for cache misses**.
5. Upserts vectors into Postgres per corpus.

In tests, **100 Kafka messages with 50 unique texts** produce **50 model invocations** and **50 `log_embeddings` rows** (same `contentId` per unique text).

Cache hits increment `embedding.cache_hits_total` and skip the model entirely.

## Envelope

```java
EmbedRequest(corpus, contentId, text, metadata)
```

| Field | Role |
|-------|------|
| `corpus` | `LOG`, `CODE`, or `INCIDENT` — selects upsert target |
| `contentId` | Natural key (`fingerprint`, incident UUID, etc.) |
| `text` | Raw text embedded (dedup key source) |
| `metadata` | CODE corpus: `repo`, `git_sha`, `file_path`, `fqn` |

Schema: `embed.requests.v1`

## Storage targets

| Corpus | Table | Upsert key |
|--------|-------|------------|
| LOG | `log_embeddings` | `fingerprint` (= `contentId`) |
| CODE | `code_embeddings` | `(repo, git_sha, file_path, fqn)` |
| INCIDENT | `incident_embeddings` | `incident_id` |

Vectors are **`vector(1536)`** for `text-embedding-3-small`.

## Metrics

- `embedding.processed_total{corpus}`
- `embedding.cache_hits_total`
- `embedding.batch_size_bucket` (SLO buckets at 16, 24, 32)

## Configuration

```yaml
embedding:
  provider: openai   # or fake (tests)
  model-name: text-embedding-3-small
  openai:
    api-key: ${OPENAI_API_KEY}
  consumer:
    max-poll-records: 32
```

## Run locally

```bash
mvn -pl service-embedding-worker -am package
docker build -f service-embedding-worker/Dockerfile service-embedding-worker
java -jar service-embedding-worker/target/service-embedding-worker-*.jar
```
