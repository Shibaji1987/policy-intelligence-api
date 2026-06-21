ALTER TABLE document_chunk
    ALTER COLUMN embedding TYPE VECTOR(384),
    ADD COLUMN embedding_model VARCHAR(120),
    ADD COLUMN embedding_dimension INTEGER,
    ADD COLUMN embedded_at TIMESTAMPTZ;

CREATE INDEX idx_document_chunk_embedding_cosine
    ON document_chunk
    USING hnsw (embedding vector_cosine_ops)
    WHERE active = true AND embedding_status = 'COMPLETED';
