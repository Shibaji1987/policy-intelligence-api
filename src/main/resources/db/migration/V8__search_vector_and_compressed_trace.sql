ALTER TABLE document_chunk
    ADD COLUMN IF NOT EXISTS search_vector tsvector
    GENERATED ALWAYS AS (to_tsvector('english', chunk_text)) STORED;

CREATE INDEX IF NOT EXISTS idx_document_chunk_search_vector
    ON document_chunk
    USING gin (search_vector)
    WHERE active = true AND embedding_status = 'COMPLETED';

ALTER TABLE retrieval_trace_source
    ADD COLUMN IF NOT EXISTS original_text TEXT,
    ADD COLUMN IF NOT EXISTS compressed_text TEXT;
