package com.shibajide.policyintelligence.embedding.application;

public record EmbeddingVector(
        String model,
        int dimension,
        float[] values
) {
}
