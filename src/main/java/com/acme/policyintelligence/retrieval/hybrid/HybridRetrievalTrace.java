package com.acme.policyintelligence.retrieval.hybrid;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record HybridRetrievalTrace(
        UUID queryId,
        String queryHash,
        String retrievalMode,
        String fusionStrategy,
        String retrievalStrategy,
        String status,
        boolean fallbackUsed,
        int topKRequested,
        int topKEffective,
        int topKReturned,
        List<String> queryVariants,
        Map<String, String> filtersApplied,
        long vectorLatencyMs,
        long keywordLatencyMs,
        long fusionLatencyMs,
        long totalLatencyMs,
        int vectorCount,
        int keywordCount,
        int fusedCount,
        String vectorStatus,
        String keywordStatus,
        String vectorFailureReason,
        String keywordFailureReason,
        List<String> warnings
) {
}
