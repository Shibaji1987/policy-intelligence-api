CREATE INDEX IF NOT EXISTS idx_document_tenant_governance
    ON document (tenant_id, department, region, document_type, classification);

CREATE INDEX IF NOT EXISTS idx_chunk_tenant_active_completed
    ON document_chunk ((metadata->>'tenantId'))
    WHERE active = true AND embedding_status = 'COMPLETED';
