package com.platform.persistence.embedding;

import com.pgvector.PGvector;
import com.platform.core.embedding.EmbedCorpus;
import com.platform.core.embedding.EmbedRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EmbeddingStore {

  private final JdbcTemplate jdbc;

  public EmbeddingStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void upsert(EmbedRequest request, float[] embedding) {
    switch (request.corpus()) {
      case LOG -> upsertLog(request, embedding);
      case CODE -> upsertCode(request, embedding);
      case INCIDENT -> upsertIncident(request, embedding);
    }
  }

  private void upsertLog(EmbedRequest request, float[] embedding) {
    jdbc.update(
        """
        INSERT INTO log_embeddings (fingerprint, content, embedding, last_seen_at)
        VALUES (?, ?, ?, now())
        ON CONFLICT (fingerprint) DO UPDATE SET
          sample_count = log_embeddings.sample_count + 1,
          last_seen_at = now(),
          content = EXCLUDED.content,
          embedding = EXCLUDED.embedding
        """,
        request.contentId(),
        request.text(),
        new PGvector(embedding));
  }

  private void upsertCode(EmbedRequest request, float[] embedding) {
    Map<String, String> meta = request.metadata();
    jdbc.update(
        """
        INSERT INTO code_embeddings (repo, git_sha, file_path, fqn, embedding)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (repo, git_sha, file_path, fqn) DO UPDATE SET
          embedding = EXCLUDED.embedding
        """,
        meta.get("repo"),
        meta.get("git_sha"),
        meta.get("file_path"),
        meta.get("fqn"),
        new PGvector(embedding));
  }

  private void upsertIncident(EmbedRequest request, float[] embedding) {
    jdbc.update(
        """
        INSERT INTO incident_embeddings (incident_id, content, embedding)
        VALUES (?, ?, ?)
        ON CONFLICT (incident_id) DO UPDATE SET
          content = EXCLUDED.content,
          embedding = EXCLUDED.embedding
        """,
        UUID.fromString(request.contentId()),
        request.text(),
        new PGvector(embedding));
  }
}
