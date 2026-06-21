package com.acme.policyintelligence.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "document_chunk")
public class DocumentChunk {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "chunk_text", nullable = false, columnDefinition = "text")
    private String chunkText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Enumerated(EnumType.STRING)
    @Column(name = "embedding_status", nullable = false, length = 30)
    private EmbeddingStatus embeddingStatus;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "embedding_model", length = 120)
    private String embeddingModel;

    @Column(name = "embedding_dimension")
    private Integer embeddingDimension;

    @Column(name = "embedded_at")
    private OffsetDateTime embeddedAt;

    @Column(name = "embedding_failure_reason", columnDefinition = "text")
    private String embeddingFailureReason;

    @Column(name = "embedding_attempts", nullable = false)
    private int embeddingAttempts;

    @Column(name = "last_embedding_attempt_at")
    private OffsetDateTime lastEmbeddingAttemptAt;

    protected DocumentChunk() {
    }

    public DocumentChunk(
            UUID documentId,
            UUID versionId,
            int chunkIndex,
            String chunkText,
            Map<String, Object> metadata
    ) {
        this.id = UUID.randomUUID();
        this.documentId = documentId;
        this.versionId = versionId;
        this.chunkIndex = chunkIndex;
        this.chunkText = chunkText;
        this.metadata = Map.copyOf(metadata);
        this.embeddingStatus = EmbeddingStatus.PENDING;
        this.active = true;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getVersionId() {
        return versionId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getChunkText() {
        return chunkText;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public EmbeddingStatus getEmbeddingStatus() {
        return embeddingStatus;
    }

    public boolean isActive() {
        return active;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public Integer getEmbeddingDimension() {
        return embeddingDimension;
    }

    public OffsetDateTime getEmbeddedAt() {
        return embeddedAt;
    }

    public String getEmbeddingFailureReason() {
        return embeddingFailureReason;
    }

    public int getEmbeddingAttempts() {
        return embeddingAttempts;
    }

    public OffsetDateTime getLastEmbeddingAttemptAt() {
        return lastEmbeddingAttemptAt;
    }
}
