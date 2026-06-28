package com.shibajide.policyintelligence.context.application;

import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;

public record ContextChunkDecision(
        RetrievedChunk chunk,
        boolean used,
        Integer contextRank,
        String reason,
        int tokenEstimate,
        int originalTokenCount,
        int compressedTokenCount,
        double compressionRatio,
        String compressionMethod,
        String originalText,
        String compressedText
) {
}
