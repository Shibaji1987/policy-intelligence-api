package com.acme.policyintelligence.retrieval.hybrid;

import com.acme.policyintelligence.retrieval.application.RetrievalFilters;
import com.acme.policyintelligence.retrieval.application.RetrievedChunk;
import com.acme.policyintelligence.retrieval.infrastructure.VectorSearchRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class KeywordRetrievalService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeywordRetrievalService.class);
    private static final int MAX_TOP_K = 100;

    private final VectorSearchRepository repository;
    private final MeterRegistry meterRegistry;

    public KeywordRetrievalService(VectorSearchRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
    }

    public List<RetrievedChunk> retrieve(String query, int topK, RetrievalFilters filters) {
        KeywordRetrievalResult result = retrieveWithMetadata(KeywordRetrievalRequest.of(query, topK, filters));
        if (result.status() == RetrievalStatus.FAILED) {
            throw new IllegalStateException(result.failureReason());
        }
        return result.chunks();
    }

    public KeywordRetrievalResult retrieveWithMetadata(String query, int topK, RetrievalFilters filters) {
        return retrieveWithMetadata(KeywordRetrievalRequest.of(query, topK, filters));
    }

    public KeywordRetrievalResult retrieveWithMetadata(KeywordRetrievalRequest request) {
        String effectiveQuery = validateQuery(request.query());
        int effectiveTopK = validateTopK(request.topK());
        RetrievalFilters effectiveFilters = request.filters();
        String queryHash = queryHash(effectiveQuery);
        Instant started = Instant.now();

        LOGGER.info(
                "Keyword retrieval started traceId={}, queryHash={}, topK={}, filters={}, searchMode={}",
                request.traceId(),
                queryHash,
                effectiveTopK,
                effectiveFilters.cacheKey(),
                request.searchMode()
        );

        try {
            List<RetrievedChunk> chunks = repository.keywordSearch(effectiveQuery, effectiveTopK, effectiveFilters);
            long latencyMs = elapsedMs(started);
            KeywordRetrievalResult result = KeywordRetrievalResult.success(
                    request.traceId(),
                    chunks,
                    queryHash,
                    request.searchMode(),
                    effectiveTopK,
                    effectiveFilters,
                    latencyMs
            );
            recordMetrics(result);
            if (result.status() == RetrievalStatus.EMPTY) {
                LOGGER.warn(
                        "Keyword retrieval empty result traceId={}, queryHash={}, topK={}",
                        request.traceId(),
                        queryHash,
                        effectiveTopK
                );
            }
            LOGGER.info(
                    "Keyword retrieval completed traceId={}, queryHash={}, status={}, resultCount={}, latencyMs={}, searchMode={}, rankingFunction={}",
                    request.traceId(),
                    queryHash,
                    result.status(),
                    result.returnedChunkCount(),
                    latencyMs,
                    result.searchMode(),
                    result.rankingFunction()
            );
            return result;
        } catch (RuntimeException exception) {
            long latencyMs = elapsedMs(started);
            KeywordRetrievalResult failure = KeywordRetrievalResult.failure(
                    request.traceId(),
                    queryHash,
                    request.searchMode(),
                    effectiveTopK,
                    effectiveFilters,
                    latencyMs,
                    safeMessage(exception)
            );
            recordMetrics(failure);
            LOGGER.warn(
                    "Keyword retrieval failed traceId={}, queryHash={}, searchMode={}, reason={}",
                    request.traceId(),
                    queryHash,
                    request.searchMode(),
                    failure.failureReason()
            );
            return failure;
        }
    }

    private String validateQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Keyword retrieval query must not be blank");
        }
        return query.strip();
    }

    private int validateTopK(int topK) {
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }
        return Math.min(topK, MAX_TOP_K);
    }

    private void recordMetrics(KeywordRetrievalResult result) {
        meterRegistry.timer("rag.keyword.search.latency").record(result.latencyMs(), TimeUnit.MILLISECONDS);
        meterRegistry.counter("rag.keyword.result.count").increment(result.returnedChunkCount());
        if (result.status() == RetrievalStatus.EMPTY) {
            meterRegistry.counter("rag.keyword.empty.count").increment();
        }
        if (result.status() == RetrievalStatus.FAILED) {
            meterRegistry.counter("rag.keyword.failure.count").increment();
        }
    }

    private String queryHash(String query) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(query.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private long elapsedMs(Instant started) {
        return Duration.between(started, Instant.now()).toMillis();
    }
}
