package com.shibajide.policyintelligence.document.application;

import com.shibajide.policyintelligence.document.domain.EmbeddingStatus;

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
        String embeddingFailureReason,
        int embeddingAttempts,
        OffsetDateTime lastEmbeddingAttemptAt,
        boolean active
) {
}
