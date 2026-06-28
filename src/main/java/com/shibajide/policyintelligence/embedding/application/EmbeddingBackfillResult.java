package com.shibajide.policyintelligence.embedding.application;

public record EmbeddingBackfillResult(
        int completed,
        int failed,
        int remainingPending,
        int remainingFailed
) {
}
