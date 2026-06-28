package com.shibajide.policyintelligence.document.application;

import com.shibajide.policyintelligence.document.domain.ChunkingStrategy;

public record IngestDocumentCommand(
        String title,
        String originalFilename,
        String mediaType,
        byte[] content,
        String tenantId,
        String department,
        String region,
        String documentType,
        String classification,
        ChunkingStrategy chunkingStrategy,
        int chunkSize,
        int chunkOverlap
) {
}
