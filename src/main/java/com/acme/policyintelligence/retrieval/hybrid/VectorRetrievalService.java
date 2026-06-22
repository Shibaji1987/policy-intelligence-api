package com.acme.policyintelligence.retrieval.hybrid;

import com.acme.policyintelligence.embedding.application.EmbeddingGenerator;
import com.acme.policyintelligence.retrieval.application.RetrievalFilters;
import com.acme.policyintelligence.retrieval.application.RetrievedChunk;
import com.acme.policyintelligence.retrieval.infrastructure.VectorSearchRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VectorRetrievalService {

    private final EmbeddingGenerator embeddingGenerator;
    private final VectorSearchRepository repository;

    public VectorRetrievalService(EmbeddingGenerator embeddingGenerator, VectorSearchRepository repository) {
        this.embeddingGenerator = embeddingGenerator;
        this.repository = repository;
    }

    public List<RetrievedChunk> retrieve(String query, int topK, RetrievalFilters filters) {
        return repository.vectorSearch(embeddingGenerator.embed(query).values(), query, topK, filters);
    }
}
