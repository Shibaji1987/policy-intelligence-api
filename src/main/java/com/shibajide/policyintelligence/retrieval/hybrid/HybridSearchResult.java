package com.shibajide.policyintelligence.retrieval.hybrid;

import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;

import java.util.List;
import java.util.UUID;

public record HybridSearchResult(
        UUID queryId,
        String originalQuery,
        String rewrittenQuery,
        List<String> expandedQueries,
        String query,
        RetrieverExecutionResult vectorResult,
        RetrieverExecutionResult keywordResult,
        com.shibajide.policyintelligence.retrieval.fusion.FusionResult fusionResult,
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
        int effectiveTopK,
        HybridRetrievalTrace trace
) {
}
