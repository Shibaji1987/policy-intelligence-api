package com.acme.policyintelligence.embedding.application;

public record EmbeddingBackfillResult(
        int completed,
        int failed,
        int remainingPending
) {
}
