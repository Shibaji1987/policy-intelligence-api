package com.acme.policyintelligence.document.application;

import com.acme.policyintelligence.document.domain.ChunkingStrategy;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentVersionSummary(
        UUID id,
        int version,
        String originalFilename,
        String mediaType,
        ChunkingStrategy chunkingStrategy,
        int chunkSize,
        int chunkOverlap,
        OffsetDateTime createdAt
) {
}
