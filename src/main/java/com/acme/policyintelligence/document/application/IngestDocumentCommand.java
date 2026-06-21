package com.acme.policyintelligence.document.application;

import com.acme.policyintelligence.document.domain.ChunkingStrategy;

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
