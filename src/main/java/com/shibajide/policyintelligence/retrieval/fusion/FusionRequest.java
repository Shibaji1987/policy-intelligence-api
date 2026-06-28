package com.shibajide.policyintelligence.retrieval.fusion;

import com.shibajide.policyintelligence.retrieval.application.RetrievalFilters;
import com.shibajide.policyintelligence.retrieval.hybrid.RetrieverExecutionResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record FusionRequest(
        UUID traceId,
        List<RetrieverExecutionResult> outcomes,
        int limit,
        RetrievalFilters filters
) {
    public FusionRequest {
        traceId = traceId == null ? UUID.randomUUID() : traceId;
        outcomes = outcomes == null ? null : Collections.unmodifiableList(new ArrayList<>(outcomes));
    }

    public static FusionRequest of(List<RetrieverExecutionResult> outcomes, int limit) {
        return new FusionRequest(UUID.randomUUID(), outcomes, limit, null);
    }
}
