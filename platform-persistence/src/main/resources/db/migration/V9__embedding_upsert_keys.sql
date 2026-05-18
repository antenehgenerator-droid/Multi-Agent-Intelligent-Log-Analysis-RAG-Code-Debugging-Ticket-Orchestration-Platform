CREATE UNIQUE INDEX IF NOT EXISTS uq_code_embeddings_natural
    ON code_embeddings (repo, git_sha, file_path, fqn);

CREATE UNIQUE INDEX IF NOT EXISTS uq_incident_embeddings_incident
    ON incident_embeddings (incident_id);
