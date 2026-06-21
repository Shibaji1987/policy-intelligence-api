package com.acme.policyintelligence.document.application;

import com.acme.policyintelligence.document.domain.ChunkingStrategy;

public record IngestDocumentCommand(
        String title,
        String originalFilename,
        String mediaType,
        byte[] content,
        ChunkingStrategy chunkingStrategy,
        int chunkSize,
        int chunkOverlap
) {
}
