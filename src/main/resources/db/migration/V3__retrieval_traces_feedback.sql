CREATE TABLE retrieval_trace (
    id UUID PRIMARY KEY,
    question TEXT NOT NULL,
    refined_query TEXT NOT NULL,
    answer TEXT NOT NULL,
    retrieved_chunks INTEGER NOT NULL,
    used_chunks INTEGER NOT NULL,
    discarded_chunks INTEGER NOT NULL,
    estimated_tokens INTEGER NOT NULL,
    top_similarity_score DOUBLE PRECISION,
    avg_top5_similarity DOUBLE PRECISION,
    document_diversity INTEGER NOT NULL,
    ml_label VARCHAR(40),
    ml_probability DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE retrieval_trace_source (
    id UUID PRIMARY KEY,
    trace_id UUID NOT NULL REFERENCES retrieval_trace(id) ON DELETE CASCADE,
    document_id UUID NOT NULL,
    document_title VARCHAR(300) NOT NULL,
    version_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    chunk_id UUID NOT NULL,
    chunk_index INTEGER NOT NULL,
    similarity_score DOUBLE PRECISION NOT NULL,
    excerpt TEXT NOT NULL,
    used_in_context BOOLEAN NOT NULL
);

CREATE TABLE retrieval_feedback (
    id UUID PRIMARY KEY,
    trace_id UUID NOT NULL REFERENCES retrieval_trace(id) ON DELETE CASCADE,
    rating VARCHAR(40) NOT NULL,
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_retrieval_trace_created_at ON retrieval_trace(created_at DESC);
CREATE INDEX idx_retrieval_trace_source_trace ON retrieval_trace_source(trace_id);
