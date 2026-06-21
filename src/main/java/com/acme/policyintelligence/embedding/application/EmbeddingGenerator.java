package com.acme.policyintelligence.embedding.application;

public interface EmbeddingGenerator {

    EmbeddingVector embed(String text);
}
