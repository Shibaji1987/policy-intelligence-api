package com.shibajide.policyintelligence.retrieval.fusion;

import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;

import java.util.List;

public record FusionResult(
        List<RetrievedChunk> results,
        FusionTrace trace
) {
    public FusionResult(List<RetrievedChunk> results) {
        this(results, null);
    }
}
