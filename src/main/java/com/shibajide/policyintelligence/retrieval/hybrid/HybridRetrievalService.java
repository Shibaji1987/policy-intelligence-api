package com.shibajide.policyintelligence.retrieval.hybrid;

import com.shibajide.policyintelligence.retrieval.application.RetrievalFilters;
import com.shibajide.policyintelligence.retrieval.application.RetrievalSupport;
import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;
import com.shibajide.policyintelligence.retrieval.fusion.FusionResult;
import com.shibajide.policyintelligence.retrieval.fusion.ReciprocalRankFusionService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

@Service
public class HybridRetrievalService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HybridRetrievalService.class);
    private static final int MAX_TOP_K = 100;

    private final VectorRetrievalService vectorRetrievalService;
    private final KeywordRetrievalService keywordRetrievalService;
    private final ReciprocalRankFusionService fusionService;
    private final MeterRegistry meterRegistry;

    public HybridRetrievalService(
            VectorRetrievalService vectorRetrievalService,
            KeywordRetrievalService keywordRetrievalService,
            ReciprocalRankFusionService fusionService,
            MeterRegistry meterRegistry
    ) {
        this.vectorRetrievalService = vectorRetrievalService;
        this.keywordRetrievalService = keywordRetrievalService;
        this.fusionService = fusionService;
        this.meterRegistry = meterRegistry;
    }

    public HybridSearchResult search(String query, int topK, RetrievalFilters filters) {
        return search(HybridSearchRequest.of(query, query, List.of(query), topK, filters));
    }

    public HybridSearchResult search(
            String originalQuery,
            String rewrittenQuery,
            List<String> expandedQueries,
            int topK,
            RetrievalFilters filters
    ) {
        return search(HybridSearchRequest.of(originalQuery, rewrittenQuery, expandedQueries, topK, filters));
    }

    public HybridSearchResult search(HybridSearchRequest request) {
        HybridExecution execution = prepare(request);
        logStarted(execution);

        RetrieverExecutionResult vector = retrieve(
                RetrievalSource.VECTOR,
                execution,
                (query, limit) -> vectorRetrievalService.retrieve(query, limit, execution.filters())
        );
        RetrieverExecutionResult keyword = retrieve(
                RetrievalSource.KEYWORD,
                execution,
                (query, limit) -> keywordRetrievalService.retrieve(query, limit, execution.filters())
        );

        FusionExecution fusion = fuse(vector, keyword, execution);
        recordMetrics(vector, keyword, fusion.fusionLatencyMs(), execution.totalLatencyMs());
        HybridRetrievalTrace trace = trace(execution, vector, keyword, fusion);
        logFinished(execution, vector, keyword, fusion.result(), trace);
        return result(execution, vector, keyword, fusion, trace);
    }

    private HybridSearchResult result(
            HybridExecution execution,
            RetrieverExecutionResult vector,
            RetrieverExecutionResult keyword,
            FusionExecution fusion,
            HybridRetrievalTrace trace
    ) {
        return new HybridSearchResult(
                execution.queryId(),
                execution.request().originalQuery(),
                execution.request().rewrittenQuery(),
                execution.queryVariants(),
                execution.query(),
                vector,
                keyword,
                fusion.result(),
                vector.results(),
                keyword.results(),
                fusion.result().results(),
                trace.retrievalStrategy(),
                trace.status(),
                vector.latencyMs(),
                keyword.latencyMs(),
                fusion.fusionLatencyMs(),
                execution.totalLatencyMs(),
                vector.failureReason(),
                keyword.failureReason(),
                execution.request().topK(),
                execution.topK(),
                trace
        );
    }

    private HybridExecution prepare(HybridSearchRequest request) {
        String query = RetrievalSupport.requireQuery(firstNonBlank(request.rewrittenQuery(), request.originalQuery()), "Hybrid retrieval");
        return new HybridExecution(
                request,
                query,
                RetrievalSupport.queryHash(query),
                validateQueryVariants(request.expandedQueries(), query),
                RetrievalSupport.requirePositiveTopK(request.topK(), MAX_TOP_K),
                request.filters(),
                Instant.now()
        );
    }

    private RetrieverExecutionResult retrieve(
            RetrievalSource source,
            HybridExecution execution,
            BiFunction<String, Integer, List<RetrievedChunk>> retriever
    ) {
        Instant started = Instant.now();
        try {
            return RetrieverExecutionResult.success(
                    source,
                    retrieveVariants(execution, retriever),
                    RetrievalSupport.elapsedMs(started)
            );
        } catch (RuntimeException exception) {
            return RetrieverExecutionResult.failure(
                    source,
                    RetrievalSupport.elapsedMs(started),
                    RetrievalSupport.safeMessage(exception)
            );
        }
    }

    private List<RetrievedChunk> retrieveVariants(
            HybridExecution execution,
            BiFunction<String, Integer, List<RetrievedChunk>> retriever
    ) {
        var merged = new LinkedHashMap<UUID, RetrievedChunk>();
        for (String queryVariant : execution.queryVariants()) {
            for (RetrievedChunk chunk : retriever.apply(queryVariant, execution.topK())) {
                merged.merge(
                        chunk.chunkId(),
                        chunk.withMatchedQueryVariant(queryVariant),
                        this::preferHigherCombinedScore
                );
            }
        }
        return List.copyOf(merged.values());
    }

    private RetrievedChunk preferHigherCombinedScore(RetrievedChunk existing, RetrievedChunk candidate) {
        return candidate.combinedScore() > existing.combinedScore() ? candidate : existing;
    }

    private FusionExecution fuse(
            RetrieverExecutionResult vector,
            RetrieverExecutionResult keyword,
            HybridExecution execution
    ) {
        Instant started = Instant.now();
        FusionResult fusion = fusionService.fuse(List.of(vector, keyword), execution.topK());
        return new FusionExecution(fusion, RetrievalSupport.elapsedMs(started));
    }

    private HybridRetrievalTrace trace(
            HybridExecution execution,
            RetrieverExecutionResult vector,
            RetrieverExecutionResult keyword,
            FusionExecution fusion
    ) {
        List<String> warnings = warnings(vector, keyword, fusion.result());
        return new HybridRetrievalTrace(
                execution.queryId(),
                execution.queryHash(),
                execution.request().retrievalMode(),
                "RRF",
                strategy(vector, keyword, fusion.result()),
                status(vector, keyword),
                vector.status() == RetrievalStatus.FAILED || keyword.status() == RetrievalStatus.FAILED,
                execution.request().topK(),
                execution.topK(),
                fusion.result().results().size(),
                execution.queryVariants(),
                RetrievalSupport.filtersApplied(execution.filters()),
                vector.latencyMs(),
                keyword.latencyMs(),
                fusion.fusionLatencyMs(),
                execution.totalLatencyMs(),
                vector.resultCount(),
                keyword.resultCount(),
                fusion.result().results().size(),
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

    private void recordMetrics(
            RetrieverExecutionResult vector,
            RetrieverExecutionResult keyword,
            long fusionLatencyMs,
            long totalLatencyMs
    ) {
        meterRegistry.timer("rag.retrieval.vector.latency").record(vector.latencyMs(), TimeUnit.MILLISECONDS);
        meterRegistry.timer("rag.retrieval.keyword.latency").record(keyword.latencyMs(), TimeUnit.MILLISECONDS);
        meterRegistry.timer("rag.retrieval.fusion.latency").record(fusionLatencyMs, TimeUnit.MILLISECONDS);
        meterRegistry.timer("rag.retrieval.hybrid.latency").record(totalLatencyMs, TimeUnit.MILLISECONDS);
        if (vector.status() == RetrievalStatus.FAILED || keyword.status() == RetrievalStatus.FAILED) {
            meterRegistry.counter("rag.retrieval.partial_failure.count").increment();
        }
        if (vector.resultCount() == 0 && keyword.resultCount() == 0) {
            meterRegistry.counter("rag.retrieval.empty_result.count").increment();
        }
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

    private void logStarted(HybridExecution execution) {
        LOGGER.info(
                "Hybrid retrieval started queryId={}, queryHash={}, topK={}, filters={}",
                execution.queryId(),
                execution.queryHash(),
                execution.topK(),
                RetrievalSupport.filtersApplied(execution.filters())
        );
    }

    private void logFinished(
            HybridExecution execution,
            RetrieverExecutionResult vector,
            RetrieverExecutionResult keyword,
            FusionResult fusion,
            HybridRetrievalTrace trace
    ) {
        if (trace.fallbackUsed()) {
            LOGGER.warn(
                    "Hybrid retrieval partial failure queryId={}, queryHash={}, vectorStatus={}, keywordStatus={}",
                    execution.queryId(),
                    execution.queryHash(),
                    vector.status(),
                    keyword.status()
            );
        }
        if (fusion.results().isEmpty()) {
            LOGGER.warn(
                    "Hybrid retrieval empty result queryId={}, queryHash={}, vectorStatus={}, keywordStatus={}",
                    execution.queryId(),
                    execution.queryHash(),
                    vector.status(),
                    keyword.status()
            );
        }
        LOGGER.info(
                "Hybrid retrieval completed queryId={}, queryHash={}, vectorCount={}, keywordCount={}, fusedCount={}, status={}, strategy={}, latencyMs={}",
                execution.queryId(),
                execution.queryHash(),
                vector.resultCount(),
                keyword.resultCount(),
                fusion.results().size(),
                trace.status(),
                trace.retrievalStrategy(),
                execution.totalLatencyMs()
        );
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first.strip() : second;
    }

    private record HybridExecution(
            HybridSearchRequest request,
            String query,
            String queryHash,
            List<String> queryVariants,
            int topK,
            RetrievalFilters filters,
            Instant started
    ) {
        UUID queryId() {
            return request.traceId();
        }

        long totalLatencyMs() {
            return RetrievalSupport.elapsedMs(started);
        }
    }

    private record FusionExecution(
            FusionResult result,
            long fusionLatencyMs
    ) {
    }
}
