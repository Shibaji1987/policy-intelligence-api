ALTER TABLE document
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(80) NOT NULL DEFAULT 'default',
    ADD COLUMN IF NOT EXISTS department VARCHAR(120),
    ADD COLUMN IF NOT EXISTS region VARCHAR(120),
    ADD COLUMN IF NOT EXISTS document_type VARCHAR(120),
    ADD COLUMN IF NOT EXISTS classification VARCHAR(120);

CREATE INDEX IF NOT EXISTS idx_document_governance_filters
    ON document (tenant_id, department, region, document_type, classification);

CREATE INDEX IF NOT EXISTS idx_document_chunk_keyword_search
    ON document_chunk
    USING gin (to_tsvector('english', chunk_text))
    WHERE active = true AND embedding_status = 'COMPLETED';

ALTER TABLE retrieval_trace
    ADD COLUMN IF NOT EXISTS retrieval_strategy VARCHAR(80) NOT NULL DEFAULT 'HYBRID_MULTI_QUERY_RERANKED',
    ADD COLUMN IF NOT EXISTS query_plan TEXT,
    ADD COLUMN IF NOT EXISTS answer_verified BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS answer_verification_reason TEXT;

ALTER TABLE retrieval_trace_source
    ADD COLUMN IF NOT EXISTS keyword_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS combined_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS retrieval_strategy VARCHAR(80) NOT NULL DEFAULT 'VECTOR';
