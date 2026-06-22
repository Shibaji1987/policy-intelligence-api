package com.acme.policyintelligence.retrieval.hybrid;

import com.acme.policyintelligence.retrieval.application.RetrievedChunk;

import java.util.List;

public record RetrieverExecutionResult(
        RetrievalSource source,
        RetrievalStatus status,
        List<RetrievedChunk> results,
        long latencyMs,
        String failureReason,
        int resultCount
) {
    public static RetrieverExecutionResult success(RetrievalSource source, List<RetrievedChunk> results, long latencyMs) {
        return new RetrieverExecutionResult(
                source,
                results.isEmpty() ? RetrievalStatus.EMPTY : RetrievalStatus.SUCCESS,
                List.copyOf(results),
                latencyMs,
                null,
                results.size()
        );
    }

    public static RetrieverExecutionResult failure(RetrievalSource source, long latencyMs, String failureReason) {
        return new RetrieverExecutionResult(source, RetrievalStatus.FAILED, List.of(), latencyMs, failureReason, 0);
    }
}
