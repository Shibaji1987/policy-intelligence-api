package com.acme.policyintelligence.embedding.application;

public record EmbeddingVector(
        String model,
        int dimension,
        float[] values
) {
}
