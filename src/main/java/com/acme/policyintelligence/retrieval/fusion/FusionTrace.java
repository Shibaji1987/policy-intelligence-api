package com.acme.policyintelligence.retrieval.fusion;

import java.util.Map;
import java.util.UUID;

public record FusionTrace(
        UUID traceId,
        String algorithm,
        int rrfK,
        int limit,
        int vectorInputCount,
        int keywordInputCount,
        int uniqueCandidateCount,
        int duplicateCount,
        int outputCount,
        long latencyMs,
        Map<String, String> filtersApplied
) {
}
