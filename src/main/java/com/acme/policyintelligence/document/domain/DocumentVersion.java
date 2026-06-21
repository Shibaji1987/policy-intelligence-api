package com.acme.policyintelligence.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "document_version")
public class DocumentVersion {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    @Column(name = "media_type", nullable = false, length = 150)
    private String mediaType;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "chunking_strategy", nullable = false, length = 40)
    private ChunkingStrategy chunkingStrategy;

    @Column(name = "chunk_size", nullable = false)
    private int chunkSize;

    @Column(name = "chunk_overlap", nullable = false)
    private int chunkOverlap;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected DocumentVersion() {
    }

    public DocumentVersion(
            UUID documentId,
            int versionNumber,
            String originalFilename,
            String mediaType,
            String contentHash,
            ChunkingStrategy chunkingStrategy,
            int chunkSize,
            int chunkOverlap
    ) {
        this.id = UUID.randomUUID();
        this.documentId = documentId;
        this.versionNumber = versionNumber;
        this.originalFilename = originalFilename;
        this.mediaType = mediaType;
        this.contentHash = contentHash;
        this.chunkingStrategy = chunkingStrategy;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getContentHash() {
        return contentHash;
    }

    public ChunkingStrategy getChunkingStrategy() {
        return chunkingStrategy;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
