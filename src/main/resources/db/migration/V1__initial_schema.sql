CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document (
    id UUID PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE document_version (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES document(id),
    version_number INTEGER NOT NULL,
    original_filename VARCHAR(500) NOT NULL,
    media_type VARCHAR(150) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    chunking_strategy VARCHAR(40) NOT NULL,
    chunk_size INTEGER NOT NULL,
    chunk_overlap INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_document_version_number UNIQUE (document_id, version_number)
);

CREATE TABLE document_chunk (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES document(id),
    version_id UUID NOT NULL REFERENCES document_version(id),
    chunk_index INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding VECTOR,
    embedding_status VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_version_chunk_index UNIQUE (version_id, chunk_index)
);

CREATE INDEX idx_document_version_document ON document_version(document_id);
CREATE INDEX idx_document_chunk_document_active ON document_chunk(document_id, active);
CREATE INDEX idx_document_chunk_version ON document_chunk(version_id);

CREATE TABLE corpus_state (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    corpus_version BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO corpus_state (id, corpus_version, updated_at)
VALUES (1, 0, CURRENT_TIMESTAMP);
