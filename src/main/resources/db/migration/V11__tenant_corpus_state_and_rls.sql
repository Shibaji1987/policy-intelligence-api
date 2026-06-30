ALTER TABLE corpus_state
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(100);

UPDATE corpus_state
SET tenant_id = 'default'
WHERE tenant_id IS NULL;

ALTER TABLE corpus_state
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE corpus_state
    DROP CONSTRAINT IF EXISTS corpus_state_pkey;

ALTER TABLE corpus_state
    DROP CONSTRAINT IF EXISTS corpus_state_id_check;

ALTER TABLE corpus_state
    ALTER COLUMN id DROP NOT NULL;

ALTER TABLE corpus_state
    ADD CONSTRAINT corpus_state_pkey PRIMARY KEY (tenant_id);

CREATE INDEX IF NOT EXISTS idx_corpus_state_updated_at
    ON corpus_state (updated_at);

ALTER TABLE document_chunk ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS chunk_tenant_isolation ON document_chunk;

CREATE POLICY chunk_tenant_isolation ON document_chunk
    USING (
        current_setting('app.tenant_id', true) IS NULL
        OR current_setting('app.tenant_id', true) = ''
        OR metadata->>'tenantId' = current_setting('app.tenant_id', true)
    );
