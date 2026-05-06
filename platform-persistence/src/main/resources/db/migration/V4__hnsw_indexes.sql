CREATE INDEX log_embeddings_hnsw ON log_embeddings USING hnsw (embedding vector_cosine_ops);
CREATE INDEX code_embeddings_hnsw ON code_embeddings USING hnsw (embedding vector_cosine_ops);
CREATE INDEX incident_embeddings_hnsw ON incident_embeddings USING hnsw (embedding vector_cosine_ops);
