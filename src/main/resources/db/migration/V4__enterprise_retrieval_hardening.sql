DROP INDEX IF EXISTS idx_document_chunk_embedding_cosine;

ALTER TABLE document_chunk
    ADD COLUMN IF NOT EXISTS embedding_failure_reason TEXT,
    ADD COLUMN IF NOT EXISTS embedding_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_embedding_attempt_at TIMESTAMPTZ;

UPDATE document_chunk
SET embedding = NULL,
    embedding_status = 'PENDING',
    embedding_model = NULL,
    embedding_dimension = NULL,
    embedded_at = NULL,
    embedding_failure_reason = NULL
WHERE active = true
  AND embedding_dimension IS DISTINCT FROM 1536;

ALTER TABLE document_chunk
    ALTER COLUMN embedding TYPE VECTOR(1536);

CREATE INDEX idx_document_chunk_embedding_cosine
    ON document_chunk
    USING hnsw (embedding vector_cosine_ops)
    WHERE active = true AND embedding_status = 'COMPLETED';

ALTER TABLE retrieval_trace
    ADD COLUMN IF NOT EXISTS corpus_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cache_hit BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS retrieval_latency_ms BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS context_build_latency_ms BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS llm_latency_ms BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ml_latency_ms BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_latency_ms BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS answer_generator VARCHAR(80) NOT NULL DEFAULT 'unknown';

ALTER TABLE retrieval_trace_source
    ADD COLUMN IF NOT EXISTS source_rank INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS context_rank INTEGER,
    ADD COLUMN IF NOT EXISTS discard_reason VARCHAR(80),
    ADD COLUMN IF NOT EXISTS token_estimate INTEGER NOT NULL DEFAULT 0;
