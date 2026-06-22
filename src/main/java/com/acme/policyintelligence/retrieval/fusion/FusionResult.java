package com.acme.policyintelligence.retrieval.fusion;

import com.acme.policyintelligence.retrieval.application.RetrievedChunk;

import java.util.List;

public record FusionResult(
        List<RetrievedChunk> results,
        FusionTrace trace
) {
    public FusionResult(List<RetrievedChunk> results) {
        this(results, null);
    }
}
