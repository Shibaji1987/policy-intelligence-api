package com.acme.policyintelligence.retrieval.hybrid;

import com.acme.policyintelligence.retrieval.application.RetrievalFilters;

import java.util.List;
import java.util.UUID;

public record HybridSearchRequest(
        UUID traceId,
        String originalQuery,
        String rewrittenQuery,
        List<String> expandedQueries,
        int topK,
        RetrievalFilters filters,
        String retrievalMode
) {
    public HybridSearchRequest {
        traceId = traceId == null ? UUID.randomUUID() : traceId;
        expandedQueries = expandedQueries == null ? List.of() : List.copyOf(expandedQueries);
        filters = filters == null ? RetrievalFilters.defaults() : filters;
        retrievalMode = retrievalMode == null || retrievalMode.isBlank() ? "HYBRID" : retrievalMode.strip();
    }

    public static HybridSearchRequest of(
            String originalQuery,
            String rewrittenQuery,
            List<String> expandedQueries,
            int topK,
            RetrievalFilters filters
    ) {
        return new HybridSearchRequest(
                UUID.randomUUID(),
                originalQuery,
                rewrittenQuery,
                expandedQueries,
                topK,
                filters,
                "HYBRID"
        );
    }
}
