package com.acme.policyintelligence.retrieval.application;

import java.util.UUID;

public record RetrievedChunk(
        UUID documentId,
        String documentTitle,
        UUID versionId,
        int version,
        UUID chunkId,
        int chunkIndex,
        String parentSectionId,
        String parentSectionTitle,
        String chunkText,
        double similarityScore,
        double keywordScore,
        double combinedScore,
        String retrievalStrategy,
        String excerpt
) {
}
