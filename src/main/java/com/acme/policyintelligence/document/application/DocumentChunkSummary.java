package com.acme.policyintelligence.document.application;

import com.acme.policyintelligence.document.domain.EmbeddingStatus;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record DocumentChunkSummary(
        UUID id,
        UUID documentId,
        UUID versionId,
        int chunkIndex,
        String chunkText,
        Map<String, Object> metadata,
        EmbeddingStatus embeddingStatus,
        String embeddingModel,
        Integer embeddingDimension,
        OffsetDateTime embeddedAt,
        boolean active
) {
}
