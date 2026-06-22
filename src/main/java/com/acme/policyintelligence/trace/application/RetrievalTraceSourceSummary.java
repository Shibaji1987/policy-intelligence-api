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
        String parentSectionId,
        String parentSectionTitle,
        double similarityScore,
        String excerpt,
        boolean usedInContext,
        int sourceRank,
        Integer contextRank,
        String discardReason,
        int tokenEstimate,
        double keywordScore,
        double combinedScore,
        String retrievalStrategy,
        Integer vectorRank,
        Integer keywordRank,
        double vectorScore,
        double rrfScore,
        String retrievalSource,
        Integer rerankRank,
        double rerankScore,
        String rerankReason,
        int originalTokenCount,
        int compressedTokenCount,
        double compressionRatio,
        String compressionMethod
) {
}
