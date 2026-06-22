package com.acme.policyintelligence.retrieval.hybrid;

import com.acme.policyintelligence.retrieval.application.RetrievalFilters;
import com.acme.policyintelligence.retrieval.application.RetrievedChunk;

import java.util.List;

public record VectorRetrievalResult(
        RetrievalStatus status,
        List<RetrievedChunk> chunks,
        String queryHash,
        String embeddingModel,
        int embeddingDimension,
        String similarityMetric,
        double similarityThreshold,
        int requestedTopK,
        int returnedChunkCount,
        RetrievalFilters filters,
        long embeddingLatencyMs,
        long vectorDbLatencyMs,
        long totalLatencyMs,
        String failureReason
) {
    public VectorRetrievalResult {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    public static VectorRetrievalResult success(
            List<RetrievedChunk> chunks,
            String queryHash,
            String embeddingModel,
            int embeddingDimension,
            int requestedTopK,
            RetrievalFilters filters,
            long embeddingLatencyMs,
            long vectorDbLatencyMs,
            long totalLatencyMs
    ) {
        RetrievalStatus status = chunks.isEmpty() ? RetrievalStatus.EMPTY : RetrievalStatus.SUCCESS;
        return new VectorRetrievalResult(
                status,
                chunks,
                queryHash,
                embeddingModel,
                embeddingDimension,
                "COSINE",
                0,
                requestedTopK,
                chunks.size(),
                filters,
                embeddingLatencyMs,
                vectorDbLatencyMs,
                totalLatencyMs,
                null
        );
    }

    public static VectorRetrievalResult failure(
            String queryHash,
            String embeddingModel,
            int embeddingDimension,
            int requestedTopK,
            RetrievalFilters filters,
            long embeddingLatencyMs,
            long vectorDbLatencyMs,
            long totalLatencyMs,
            String failureReason
    ) {
        return new VectorRetrievalResult(
                RetrievalStatus.FAILED,
                List.of(),
                queryHash,
                embeddingModel,
                embeddingDimension,
                "COSINE",
                0,
                requestedTopK,
                0,
                filters,
                embeddingLatencyMs,
                vectorDbLatencyMs,
                totalLatencyMs,
                failureReason
        );
    }
}
