package com.acme.policyintelligence.retrieval.application;

import java.util.List;

public record RetrievalSearchResponse(
        String query,
        int requestedTopK,
        int returnedChunks,
        String embeddingModel,
        int embeddingDimension,
        boolean cacheHit,
        List<RetrievedChunk> chunks
) {
}
