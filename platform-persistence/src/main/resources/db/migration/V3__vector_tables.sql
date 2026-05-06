CREATE TABLE log_embeddings (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                fingerprint TEXT NOT NULL UNIQUE,
                                content TEXT NOT NULL,
                                embedding vector(1536) NOT NULL,
                                sample_count INT NOT NULL DEFAULT 1,
                                last_seen_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE code_embeddings (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 repo TEXT NOT NULL,
                                 git_sha TEXT NOT NULL,
                                 file_path TEXT NOT NULL,
                                 fqn TEXT NOT NULL,
                                 embedding vector(1536) NOT NULL
);

CREATE TABLE incident_embeddings (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     incident_id UUID NOT NULL REFERENCES incidents(id),
                                     content TEXT NOT NULL,
                                     embedding vector(1536) NOT NULL
);
