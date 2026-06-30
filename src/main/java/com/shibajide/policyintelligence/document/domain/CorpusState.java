package com.shibajide.policyintelligence.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "corpus_state")
public class CorpusState {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "corpus_version", nullable = false)
    private long corpusVersion;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected CorpusState() {
    }

    public CorpusState(String tenantId) {
        this.tenantId = tenantId;
        this.corpusVersion = 0;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public long increment() {
        corpusVersion++;
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        return corpusVersion;
    }

    public long getCorpusVersion() {
        return corpusVersion;
    }

    public String getTenantId() {
        return tenantId;
    }
}
