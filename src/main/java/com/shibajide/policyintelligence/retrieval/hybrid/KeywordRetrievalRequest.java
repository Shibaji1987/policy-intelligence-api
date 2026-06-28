package com.shibajide.policyintelligence.retrieval.hybrid;

import com.shibajide.policyintelligence.retrieval.application.RetrievalFilters;

import java.util.UUID;

public record KeywordRetrievalRequest(
        UUID traceId,
        String query,
        int topK,
        RetrievalFilters filters,
        String searchMode
) {
    public KeywordRetrievalRequest {
        traceId = traceId == null ? UUID.randomUUID() : traceId;
        filters = filters == null ? RetrievalFilters.defaults() : filters;
        searchMode = searchMode == null || searchMode.isBlank() ? "POSTGRES_FULL_TEXT" : searchMode.strip();
    }

    public static KeywordRetrievalRequest of(String query, int topK, RetrievalFilters filters) {
        return new KeywordRetrievalRequest(UUID.randomUUID(), query, topK, filters, "POSTGRES_FULL_TEXT");
    }
}
