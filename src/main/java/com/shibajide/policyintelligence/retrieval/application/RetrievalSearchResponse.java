package com.shibajide.policyintelligence.retrieval.application;

import java.util.List;

public record RetrievalSearchResponse(
        String query,
        int requestedTopK,
        int returnedChunks,
        String embeddingModel,
        int embeddingDimension,
        long corpusVersion,
        boolean cacheHit,
        RetrievalTokenUsage tokenUsage,
        List<RetrievedChunk> chunks
) {
}
