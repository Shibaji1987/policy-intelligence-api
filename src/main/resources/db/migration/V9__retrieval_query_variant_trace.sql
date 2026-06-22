ALTER TABLE retrieval_trace_source
    ADD COLUMN IF NOT EXISTS matched_query_variant TEXT;
