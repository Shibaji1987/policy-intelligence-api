package com.acme.policyintelligence.retrieval.hybrid;

import com.acme.policyintelligence.retrieval.application.RetrievedChunk;

import java.util.List;

public record HybridSearchResult(
        String query,
        List<RetrievedChunk> vectorResults,
        List<RetrievedChunk> keywordResults,
        List<RetrievedChunk> fusedResults,
        String retrievalStrategy,
        String status,
        long vectorLatencyMs,
        long keywordLatencyMs,
        long fusionLatencyMs,
        long totalLatencyMs,
        String vectorError,
        String keywordError,
        int requestedTopK,
        int effectiveTopK
) {
}
