package com.acme.policyintelligence.retrieval.hybrid;

import com.acme.policyintelligence.retrieval.application.RetrievalFilters;
import com.acme.policyintelligence.retrieval.application.RetrievedChunk;
import com.acme.policyintelligence.retrieval.fusion.FusionResult;
import com.acme.policyintelligence.retrieval.fusion.ReciprocalRankFusionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

@Service
public class HybridRetrievalService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HybridRetrievalService.class);
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
        return search(query, query, List.of(query), topK, filters);
    }

    public HybridSearchResult search(
            String originalQuery,
            String rewrittenQuery,
            List<String> expandedQueries,
            int topK,
            RetrievalFilters filters
    ) {
        Instant started = Instant.now();
        UUID queryId = UUID.randomUUID();
        String effectiveQuery = validateQuery(firstNonBlank(rewrittenQuery, originalQuery));
        List<String> queryVariants = validateQueryVariants(expandedQueries, effectiveQuery);
        int effectiveTopK = validateTopK(topK);
        RetrievalFilters effectiveFilters = filters == null ? RetrievalFilters.defaults() : filters;

        RetrieverExecutionResult vector = safelyRetrieve(
                RetrievalSource.VECTOR,
                queryVariants,
                effectiveTopK,
                effectiveFilters,
                (query, limit) -> vectorRetrievalService.retrieve(query, limit, effectiveFilters)
        );
        RetrieverExecutionResult keyword = safelyRetrieve(
                RetrievalSource.KEYWORD,
                queryVariants,
                effectiveTopK,
                effectiveFilters,
                (query, limit) -> keywordRetrievalService.retrieve(query, limit, effectiveFilters)
        );

        Instant fusionStarted = Instant.now();
        FusionResult fusion = fusionService.fuse(vector.results(), keyword.results(), effectiveTopK);
        long fusionLatencyMs = elapsedMs(fusionStarted);
        long totalLatencyMs = elapsedMs(started);
        HybridRetrievalTrace trace = trace(
                queryId,
                vector,
                keyword,
                fusion,
                queryVariants,
                topK,
                effectiveTopK,
                effectiveFilters,
                fusionLatencyMs,
                totalLatencyMs
        );

        LOGGER.info(
                "Hybrid retrieval completed queryId={}, vectorCount={}, keywordCount={}, fusedCount={}, status={}, strategy={}, latencyMs={}",
                queryId,
                vector.resultCount(),
                keyword.resultCount(),
                fusion.results().size(),
                trace.status(),
                trace.retrievalStrategy(),
                totalLatencyMs
        );

        return new HybridSearchResult(
                queryId,
                originalQuery,
                rewrittenQuery,
                queryVariants,
                effectiveQuery,
                vector,
                keyword,
                fusion,
                vector.results(),
                keyword.results(),
                fusion.results(),
                trace.retrievalStrategy(),
                trace.status(),
                vector.latencyMs(),
                keyword.latencyMs(),
                fusionLatencyMs,
                totalLatencyMs,
                vector.failureReason(),
                keyword.failureReason(),
                topK,
                effectiveTopK,
                trace
        );
    }

    private RetrieverExecutionResult safelyRetrieve(
            RetrievalSource source,
            List<String> queryVariants,
            int topK,
            RetrievalFilters filters,
            BiFunction<String, Integer, List<RetrievedChunk>> retriever
    ) {
        Instant started = Instant.now();
        try {
            var merged = new LinkedHashMap<UUID, RetrievedChunk>();
            for (String queryVariant : queryVariants) {
                for (RetrievedChunk chunk : retriever.apply(queryVariant, topK)) {
                    merged.merge(
                            chunk.chunkId(),
                            chunk.withMatchedQueryVariant(queryVariant),
                            (existing, candidate) -> candidate.combinedScore() > existing.combinedScore() ? candidate : existing
                    );
                }
            }
            return RetrieverExecutionResult.success(source, List.copyOf(merged.values()), elapsedMs(started));
        } catch (RuntimeException exception) {
            return RetrieverExecutionResult.failure(source, elapsedMs(started), safeMessage(exception));
        }
    }

    private HybridRetrievalTrace trace(
            UUID queryId,
            RetrieverExecutionResult vector,
            RetrieverExecutionResult keyword,
            FusionResult fusion,
            List<String> queryVariants,
            int requestedTopK,
            int effectiveTopK,
            RetrievalFilters filters,
            long fusionLatencyMs,
            long totalLatencyMs
    ) {
        List<String> warnings = warnings(vector, keyword, fusion);
        return new HybridRetrievalTrace(
                queryId,
                "HYBRID",
                "RRF",
                strategy(vector, keyword, fusion),
                status(vector, keyword),
                vector.status() == RetrievalStatus.FAILED || keyword.status() == RetrievalStatus.FAILED,
                requestedTopK,
                effectiveTopK,
                fusion.results().size(),
                queryVariants,
                filtersApplied(filters),
                vector.latencyMs(),
                keyword.latencyMs(),
                fusionLatencyMs,
                totalLatencyMs,
                vector.resultCount(),
                keyword.resultCount(),
                fusion.results().size(),
                vector.status().name(),
                keyword.status().name(),
                vector.failureReason(),
                keyword.failureReason(),
                warnings
        );
    }

    private List<String> warnings(RetrieverExecutionResult vector, RetrieverExecutionResult keyword, FusionResult fusion) {
        var warnings = new ArrayList<String>();
        if (vector.status() == RetrievalStatus.FAILED) {
            warnings.add("VECTOR_RETRIEVER_FAILED");
        } else if (vector.status() == RetrievalStatus.EMPTY) {
            warnings.add("VECTOR_RETRIEVER_EMPTY");
        }
        if (keyword.status() == RetrievalStatus.FAILED) {
            warnings.add("KEYWORD_RETRIEVER_FAILED");
        } else if (keyword.status() == RetrievalStatus.EMPTY) {
            warnings.add("KEYWORD_RETRIEVER_EMPTY");
        }
        if (fusion.results().isEmpty()) {
            warnings.add("FUSION_EMPTY");
        }
        return List.copyOf(warnings);
    }

    private String validateQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Hybrid retrieval query must not be blank");
        }
        return query.strip();
    }

    private List<String> validateQueryVariants(List<String> expandedQueries, String fallbackQuery) {
        var variants = new ArrayList<String>();
        if (expandedQueries != null) {
            expandedQueries.stream()
                    .filter(query -> query != null && !query.isBlank())
                    .map(String::strip)
                    .distinct()
                    .forEach(variants::add);
        }
        if (variants.isEmpty()) {
            variants.add(fallbackQuery);
        }
        return List.copyOf(variants);
    }

    private int validateTopK(int topK) {
        if (topK <= 0) {
            return DEFAULT_TOP_K;
        }
        return Math.min(topK, MAX_TOP_K);
    }

    private String strategy(RetrieverExecutionResult vector, RetrieverExecutionResult keyword, FusionResult fusion) {
        if (fusion.results().isEmpty()) {
            return "NO_RESULTS";
        }
        if (vector.status() == RetrievalStatus.SUCCESS && keyword.status() == RetrievalStatus.SUCCESS) {
            return "HYBRID_VECTOR_KEYWORD_RRF";
        }
        if (vector.status() == RetrievalStatus.SUCCESS) {
            return "VECTOR_ONLY_DEGRADED";
        }
        if (keyword.status() == RetrievalStatus.SUCCESS) {
            return "KEYWORD_ONLY_DEGRADED";
        }
        return "NO_RESULTS";
    }

    private String status(RetrieverExecutionResult vector, RetrieverExecutionResult keyword) {
        if (vector.status() == RetrievalStatus.FAILED && keyword.status() == RetrievalStatus.FAILED) {
            return "FAILED";
        }
        if (vector.status() == RetrievalStatus.FAILED || keyword.status() == RetrievalStatus.FAILED) {
            return "DEGRADED";
        }
        return "COMPLETED";
    }

    private Map<String, String> filtersApplied(RetrievalFilters filters) {
        return Map.of(
                "tenantId", value(filters.tenantId()),
                "department", value(filters.department()),
                "region", value(filters.region()),
                "documentType", value(filters.documentType()),
                "classification", value(filters.classification())
        );
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first.strip() : second;
    }

    private String value(String value) {
        return value == null ? "*" : value;
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private long elapsedMs(Instant started) {
        return Duration.between(started, Instant.now()).toMillis();
    }
}
