package com.shibajide.policyintelligence.retrieval.application;

import com.shibajide.policyintelligence.retrieval.infrastructure.VectorSearchRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChunkNeighborService {

    private final VectorSearchRepository vectorSearchRepository;

    public ChunkNeighborService(VectorSearchRepository vectorSearchRepository) {
        this.vectorSearchRepository = vectorSearchRepository;
    }

    public List<RetrievedChunk> findActiveNeighbors(List<RetrievedChunk> seeds) {
        return vectorSearchRepository.findActiveNeighbors(seeds);
    }
}
