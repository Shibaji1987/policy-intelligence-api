package com.acme.policyintelligence.retrieval.hybrid;

import com.acme.policyintelligence.retrieval.application.RetrievalFilters;
import com.acme.policyintelligence.retrieval.application.RetrievedChunk;
import com.acme.policyintelligence.retrieval.infrastructure.VectorSearchRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KeywordRetrievalService {

    private final VectorSearchRepository repository;

    public KeywordRetrievalService(VectorSearchRepository repository) {
        this.repository = repository;
    }

    public List<RetrievedChunk> retrieve(String query, int topK, RetrievalFilters filters) {
        return repository.keywordSearch(query, topK, filters);
    }
}
