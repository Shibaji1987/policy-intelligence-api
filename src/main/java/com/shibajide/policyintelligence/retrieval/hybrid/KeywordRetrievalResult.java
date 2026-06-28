package com.shibajide.policyintelligence.retrieval.hybrid;

import com.shibajide.policyintelligence.retrieval.application.RetrievalFilters;
import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;

import java.util.List;
import java.util.UUID;

public record KeywordRetrievalResult(
        UUID traceId,
        RetrievalStatus status,
        List<RetrievedChunk> chunks,
        String queryHash,
        String searchMode,
        String rankingFunction,
        String language,
        int requestedTopK,
        int returnedChunkCount,
        RetrievalFilters filters,
        long latencyMs,
        String failureReason
) {
    public KeywordRetrievalResult {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    public static KeywordRetrievalResult success(
            UUID traceId,
            List<RetrievedChunk> chunks,
            String queryHash,
            String searchMode,
            int requestedTopK,
            RetrievalFilters filters,
            long latencyMs
    ) {
        RetrievalStatus status = chunks.isEmpty() ? RetrievalStatus.EMPTY : RetrievalStatus.SUCCESS;
        return new KeywordRetrievalResult(
                traceId,
                status,
                chunks,
                queryHash,
                searchMode,
                "TS_RANK_CD",
                "english",
                requestedTopK,
                chunks.size(),
                filters,
                latencyMs,
                null
        );
    }

    public static KeywordRetrievalResult failure(
            UUID traceId,
            String queryHash,
            String searchMode,
            int requestedTopK,
            RetrievalFilters filters,
            long latencyMs,
            String failureReason
    ) {
        return new KeywordRetrievalResult(
                traceId,
                RetrievalStatus.FAILED,
                List.of(),
                queryHash,
                searchMode,
                "TS_RANK_CD",
                "english",
                requestedTopK,
                0,
                filters,
                latencyMs,
                failureReason
        );
    }
}
