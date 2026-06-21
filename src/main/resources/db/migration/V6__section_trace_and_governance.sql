ALTER TABLE retrieval_trace_source
    ADD COLUMN IF NOT EXISTS parent_section_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS parent_section_title VARCHAR(200);

CREATE INDEX IF NOT EXISTS idx_retrieval_trace_source_section
    ON retrieval_trace_source (trace_id, parent_section_id);
