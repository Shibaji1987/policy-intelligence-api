package com.acme.policyintelligence.trace.application;

import java.util.UUID;

public record RetrievalTraceSourceSummary(
        UUID id,
        UUID documentId,
        String documentTitle,
        UUID versionId,
        int versionNumber,
        UUID chunkId,
        int chunkIndex,
        double similarityScore,
        String excerpt,
        boolean usedInContext,
        int sourceRank,
        Integer contextRank,
        String discardReason,
        int tokenEstimate,
        double keywordScore,
        double combinedScore,
        String retrievalStrategy
) {
}
