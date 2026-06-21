package com.acme.policyintelligence.context.application;

public record ContextMetrics(
        int retrievedChunks,
        int usedChunks,
        int discardedChunks,
        int estimatedTokens,
        int documentDiversity
) {
}
