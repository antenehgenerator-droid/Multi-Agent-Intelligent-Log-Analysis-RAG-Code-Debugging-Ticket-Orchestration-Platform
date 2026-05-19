ALTER TABLE code_embeddings
    ADD COLUMN IF NOT EXISTS content TEXT;

UPDATE code_embeddings
SET content = file_path || ' ' || fqn
WHERE content IS NULL;
