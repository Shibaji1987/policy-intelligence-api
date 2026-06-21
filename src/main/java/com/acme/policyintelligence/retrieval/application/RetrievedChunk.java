package com.acme.policyintelligence.retrieval.application;

import java.util.UUID;

public record RetrievedChunk(
        UUID documentId,
        String documentTitle,
        UUID versionId,
        int version,
        UUID chunkId,
        int chunkIndex,
        String chunkText,
        double similarityScore,
        String excerpt
) {
}
