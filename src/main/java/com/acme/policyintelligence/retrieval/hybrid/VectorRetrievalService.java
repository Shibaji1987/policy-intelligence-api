package com.acme.policyintelligence.retrieval.hybrid;

import com.acme.policyintelligence.embedding.application.EmbeddingGenerator;
import com.acme.policyintelligence.embedding.application.EmbeddingVector;
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
public class VectorRetrievalService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorRetrievalService.class);
    private static final int MAX_TOP_K = 100;

    private final EmbeddingGenerator embeddingGenerator;
    private final VectorSearchRepository repository;
    private final MeterRegistry meterRegistry;

    public VectorRetrievalService(
            EmbeddingGenerator embeddingGenerator,
            VectorSearchRepository repository,
            MeterRegistry meterRegistry
    ) {
        this.embeddingGenerator = embeddingGenerator;
        this.repository = repository;
        this.meterRegistry = meterRegistry;
    }

    public List<RetrievedChunk> retrieve(String query, int topK, RetrievalFilters filters) {
        VectorRetrievalResult result = retrieveWithMetadata(query, topK, filters);
        if (result.status() == RetrievalStatus.FAILED) {
            throw new IllegalStateException(result.failureReason());
        }
        return result.chunks();
    }

    public VectorRetrievalResult retrieveWithMetadata(String query, int topK, RetrievalFilters filters) {
        String effectiveQuery = validateQuery(query);
        int effectiveTopK = validateTopK(topK);
        RetrievalFilters effectiveFilters = filters == null ? RetrievalFilters.defaults() : filters;
        String queryHash = queryHash(effectiveQuery);
        Instant totalStarted = Instant.now();
        long embeddingLatencyMs = 0;
        long vectorDbLatencyMs = 0;
        String embeddingModel = null;
        int embeddingDimension = 0;
        boolean embeddingCompleted = false;

        LOGGER.info(
                "Vector retrieval started queryHash={}, topK={}, filters={}",
                queryHash,
                effectiveTopK,
                filtersApplied(effectiveFilters)
        );

        try {
            Instant embeddingStarted = Instant.now();
            EmbeddingVector embedding = embeddingGenerator.embed(effectiveQuery);
            embeddingLatencyMs = elapsedMs(embeddingStarted);
            embeddingModel = embedding.model();
            embeddingDimension = embedding.dimension();
            validateEmbedding(embedding);
            embeddingCompleted = true;

            Instant vectorDbStarted = Instant.now();
            List<RetrievedChunk> chunks = repository.vectorSearch(
                    embedding.values(),
                    effectiveQuery,
                    effectiveTopK,
                    effectiveFilters
            );
            vectorDbLatencyMs = elapsedMs(vectorDbStarted);
            long totalLatencyMs = elapsedMs(totalStarted);
            VectorRetrievalResult result = VectorRetrievalResult.success(
                    chunks,
                    queryHash,
                    embeddingModel,
                    embeddingDimension,
                    effectiveTopK,
                    effectiveFilters,
                    embeddingLatencyMs,
                    vectorDbLatencyMs,
                    totalLatencyMs
            );
            recordMetrics(result, false, false);
            if (result.status() == RetrievalStatus.EMPTY) {
                LOGGER.warn("Vector retrieval empty result queryHash={}, topK={}", queryHash, effectiveTopK);
            }
            LOGGER.info(
                    "Vector retrieval completed queryHash={}, status={}, returnedChunks={}, embeddingLatencyMs={}, vectorDbLatencyMs={}, totalLatencyMs={}",
                    queryHash,
                    result.status(),
                    result.returnedChunkCount(),
                    embeddingLatencyMs,
                    vectorDbLatencyMs,
                    totalLatencyMs
            );
            return result;
        } catch (RuntimeException exception) {
            long totalLatencyMs = elapsedMs(totalStarted);
            boolean embeddingFailure = !embeddingCompleted;
            boolean pgVectorFailure = embeddingCompleted && vectorDbLatencyMs == 0;
            VectorRetrievalResult failure = VectorRetrievalResult.failure(
                    queryHash,
                    embeddingModel,
                    embeddingDimension,
                    effectiveTopK,
                    effectiveFilters,
                    embeddingLatencyMs,
                    vectorDbLatencyMs,
                    totalLatencyMs,
                    safeMessage(exception)
            );
            recordMetrics(failure, embeddingFailure, pgVectorFailure);
            LOGGER.warn(
                    "Vector retrieval failed queryHash={}, embeddingFailure={}, pgVectorFailure={}, reason={}",
                    queryHash,
                    embeddingFailure,
                    pgVectorFailure,
                    failure.failureReason()
            );
            return failure;
        }
    }

    private String validateQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Vector retrieval query must not be blank");
        }
        return query.strip();
    }

    private int validateTopK(int topK) {
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }
        return Math.min(topK, MAX_TOP_K);
    }

    private void validateEmbedding(EmbeddingVector embedding) {
        if (embedding == null || embedding.values() == null || embedding.values().length == 0) {
            throw new IllegalStateException("Embedding generator returned an empty vector");
        }
    }

    private void recordMetrics(VectorRetrievalResult result, boolean embeddingFailure, boolean pgVectorFailure) {
        meterRegistry.timer("rag.vector.embedding.latency")
                .record(result.embeddingLatencyMs(), TimeUnit.MILLISECONDS);
        meterRegistry.timer("rag.vector.search.latency")
                .record(result.vectorDbLatencyMs(), TimeUnit.MILLISECONDS);
        meterRegistry.timer("rag.vector.total.latency")
                .record(result.totalLatencyMs(), TimeUnit.MILLISECONDS);
        meterRegistry.counter("rag.vector.result.count").increment(result.returnedChunkCount());
        if (result.status() == RetrievalStatus.EMPTY) {
            meterRegistry.counter("rag.vector.empty.count").increment();
        }
        if (result.status() == RetrievalStatus.FAILED) {
            meterRegistry.counter("rag.vector.failure.count").increment();
        }
        if (embeddingFailure) {
            meterRegistry.counter("rag.vector.embedding.failure.count").increment();
        }
        if (pgVectorFailure) {
            meterRegistry.counter("rag.vector.pgvector.failure.count").increment();
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

    private String filtersApplied(RetrievalFilters filters) {
        return filters.cacheKey();
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private long elapsedMs(Instant started) {
        return Duration.between(started, Instant.now()).toMillis();
    }
}
