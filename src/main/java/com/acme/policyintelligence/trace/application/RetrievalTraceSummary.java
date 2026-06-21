package com.acme.policyintelligence.trace.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RetrievalTraceSummary(
        UUID id,
        String question,
        String refinedQuery,
        String answer,
        int retrievedChunks,
        int usedChunks,
        int discardedChunks,
        int estimatedTokens,
        Double topSimilarityScore,
        Double avgTop5Similarity,
        int documentDiversity,
        String mlLabel,
        Double mlProbability,
        OffsetDateTime createdAt
) {
}
