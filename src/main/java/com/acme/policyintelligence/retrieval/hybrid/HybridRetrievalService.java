package com.acme.policyintelligence.retrieval.hybrid;

import com.acme.policyintelligence.retrieval.application.RetrievalFilters;
import com.acme.policyintelligence.retrieval.fusion.ReciprocalRankFusionService;
import org.springframework.stereotype.Service;

@Service
public class HybridRetrievalService {

    private final VectorRetrievalService vectorRetrievalService;
    private final KeywordRetrievalService keywordRetrievalService;
    private final ReciprocalRankFusionService fusionService;

    public HybridRetrievalService(
            VectorRetrievalService vectorRetrievalService,
            KeywordRetrievalService keywordRetrievalService,
            ReciprocalRankFusionService fusionService
    ) {
        this.vectorRetrievalService = vectorRetrievalService;
        this.keywordRetrievalService = keywordRetrievalService;
        this.fusionService = fusionService;
    }

    public HybridSearchResult search(String query, int topK, RetrievalFilters filters) {
        var vector = vectorRetrievalService.retrieve(query, topK, filters);
        var keyword = keywordRetrievalService.retrieve(query, topK, filters);
        var fused = fusionService.fuse(vector, keyword, topK).results();
        return new HybridSearchResult(query, vector, keyword, fused);
    }
}
