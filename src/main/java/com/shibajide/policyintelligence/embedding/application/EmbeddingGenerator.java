package com.shibajide.policyintelligence.embedding.application;

public interface EmbeddingGenerator {

    EmbeddingVector embed(String text);
}
