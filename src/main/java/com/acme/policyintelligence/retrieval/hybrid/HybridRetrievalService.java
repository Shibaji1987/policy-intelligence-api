package com.acme.policyintelligence.retrieval.hybrid;

import com.acme.policyintelligence.retrieval.application.RetrievalFilters;
import com.acme.policyintelligence.retrieval.application.RetrievedChunk;
import com.acme.policyintelligence.retrieval.fusion.ReciprocalRankFusionService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class HybridRetrievalService {

    private static final int DEFAULT_TOP_K = 20;
    private static final int MAX_TOP_K = 100;

    private final VectorRetrievalService vectorRetrievalService;
    private final KeywordRetrievalService keywordRetrievalService;
    private final ReciprocalRankFusionService fusionService;

    public HybridRetrievalService(
            VectorRetrievalService vectorRetrievalService,
            KeywordRetrievalService keywordRetrievalService,
            ReciprocalRankFusionService fusionService
    ) {
        this.vectorRetrievalService = vectorRetrievalService;
        this.keywordRetrievalService = keywordRetrievalService;
        this.fusionService = fusionService;
    }

    public HybridSearchResult search(String query, int topK, RetrievalFilters filters) {
        Instant started = Instant.now();
        String effectiveQuery = validateQuery(query);
        int effectiveTopK = validateTopK(topK);

        TimedRetrieval vector = retrieveVector(effectiveQuery, effectiveTopK, filters);
        TimedRetrieval keyword = retrieveKeyword(effectiveQuery, effectiveTopK, filters);

        Instant fusionStarted = Instant.now();
        List<RetrievedChunk> fused = fusionService.fuse(vector.results(), keyword.results(), effectiveTopK).results();
        long fusionLatencyMs = elapsedMs(fusionStarted);

        return new HybridSearchResult(
                effectiveQuery,
                vector.results(),
                keyword.results(),
                fused,
                strategy(vector, keyword),
                status(vector, keyword),
                vector.latencyMs(),
                keyword.latencyMs(),
                fusionLatencyMs,
                elapsedMs(started),
                vector.error(),
                keyword.error(),
                topK,
                effectiveTopK
        );
    }

    private TimedRetrieval retrieveVector(String query, int topK, RetrievalFilters filters) {
        Instant started = Instant.now();
        try {
            return new TimedRetrieval(vectorRetrievalService.retrieve(query, topK, filters), elapsedMs(started), null);
        } catch (RuntimeException exception) {
            return new TimedRetrieval(List.of(), elapsedMs(started), safeMessage(exception));
        }
    }

    private TimedRetrieval retrieveKeyword(String query, int topK, RetrievalFilters filters) {
        Instant started = Instant.now();
        try {
            return new TimedRetrieval(keywordRetrievalService.retrieve(query, topK, filters), elapsedMs(started), null);
        } catch (RuntimeException exception) {
            return new TimedRetrieval(List.of(), elapsedMs(started), safeMessage(exception));
        }
    }

    private String validateQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Hybrid retrieval query must not be blank");
        }
        return query.strip();
    }

    private int validateTopK(int topK) {
        if (topK <= 0) {
            return DEFAULT_TOP_K;
        }
        return Math.min(topK, MAX_TOP_K);
    }

    private String strategy(TimedRetrieval vector, TimedRetrieval keyword) {
        if (!vector.results().isEmpty() && !keyword.results().isEmpty()) {
            return "HYBRID_VECTOR_KEYWORD_RRF";
        }
        if (!vector.results().isEmpty()) {
            return "VECTOR_ONLY_DEGRADED";
        }
        if (!keyword.results().isEmpty()) {
            return "KEYWORD_ONLY_DEGRADED";
        }
        return "NO_RESULTS";
    }

    private String status(TimedRetrieval vector, TimedRetrieval keyword) {
        if (vector.error() == null && keyword.error() == null) {
            return "COMPLETED";
        }
        if (!vector.results().isEmpty() || !keyword.results().isEmpty()) {
            return "DEGRADED";
        }
        return "FAILED";
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private long elapsedMs(Instant started) {
        return Duration.between(started, Instant.now()).toMillis();
    }

    private record TimedRetrieval(
            List<RetrievedChunk> results,
            long latencyMs,
            String error
    ) {
    }
}
