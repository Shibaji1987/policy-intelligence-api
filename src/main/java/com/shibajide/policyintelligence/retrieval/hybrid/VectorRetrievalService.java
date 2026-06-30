package com.shibajide.policyintelligence.retrieval.hybrid;

import com.shibajide.policyintelligence.embedding.application.EmbeddingGenerator;
import com.shibajide.policyintelligence.embedding.application.EmbeddingVector;
import com.shibajide.policyintelligence.retrieval.application.RetrievalFilters;
import com.shibajide.policyintelligence.retrieval.application.RetrievalSupport;
import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;
import com.shibajide.policyintelligence.retrieval.infrastructure.VectorSearchRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
        VectorRetrievalRequest request = prepare(query, topK, filters);
        VectorRetrievalTiming timing = new VectorRetrievalTiming(Instant.now());

        logStarted(request);

        try {
            VectorEmbedding embedding = embed(request, timing);
            List<RetrievedChunk> chunks = searchVectorStore(request, embedding, timing);
            VectorRetrievalResult result = success(request, embedding, chunks, timing);
            recordMetrics(result, false, false);
            logCompleted(result);
            return result;
        } catch (RuntimeException exception) {
            boolean embeddingFailure = !timing.embeddingCompleted;
            boolean pgVectorFailure = timing.embeddingCompleted && timing.vectorDbLatencyMs == 0;
            VectorRetrievalResult failure = failure(request, timing, exception);
            recordMetrics(failure, embeddingFailure, pgVectorFailure);
            logFailed(failure, embeddingFailure, pgVectorFailure);
            return failure;
        }
    }

    private VectorRetrievalRequest prepare(String query, int topK, RetrievalFilters filters) {
        String effectiveQuery = RetrievalSupport.requireQuery(query, "Vector retrieval");
        return new VectorRetrievalRequest(
                effectiveQuery,
                RetrievalSupport.queryHash(effectiveQuery),
                RetrievalSupport.requirePositiveTopK(topK, MAX_TOP_K),
                filters == null ? RetrievalFilters.defaults() : filters
        );
    }

    private VectorEmbedding embed(VectorRetrievalRequest request, VectorRetrievalTiming timing) {
        Instant started = Instant.now();
        EmbeddingVector embedding = embeddingGenerator.embed(request.query());
        timing.embeddingLatencyMs = RetrievalSupport.elapsedMs(started);
        validateEmbedding(embedding);
        timing.embeddingModel = embedding.model();
        timing.embeddingDimension = embedding.dimension();
        timing.embeddingCompleted = true;
        return new VectorEmbedding(embedding);
    }

    private List<RetrievedChunk> searchVectorStore(
            VectorRetrievalRequest request,
            VectorEmbedding embedding,
            VectorRetrievalTiming timing
    ) {
        Instant started = Instant.now();
        List<RetrievedChunk> chunks = repository.vectorSearch(
                embedding.values(),
                request.query(),
                request.topK(),
                request.filters()
        );
        timing.vectorDbLatencyMs = RetrievalSupport.elapsedMs(started);
        return chunks;
    }

    private VectorRetrievalResult success(
            VectorRetrievalRequest request,
            VectorEmbedding embedding,
            List<RetrievedChunk> chunks,
            VectorRetrievalTiming timing
    ) {
        return VectorRetrievalResult.success(
                chunks,
                request.queryHash(),
                embedding.model(),
                embedding.dimension(),
                request.topK(),
                request.filters(),
                timing.embeddingLatencyMs,
                timing.vectorDbLatencyMs,
                timing.totalLatencyMs()
        );
    }

    private VectorRetrievalResult failure(
            VectorRetrievalRequest request,
            VectorRetrievalTiming timing,
            RuntimeException exception
    ) {
        return VectorRetrievalResult.failure(
                request.queryHash(),
                timing.embeddingModel,
                timing.embeddingDimension,
                request.topK(),
                request.filters(),
                timing.embeddingLatencyMs,
                timing.vectorDbLatencyMs,
                timing.totalLatencyMs(),
                RetrievalSupport.safeMessage(exception)
        );
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

    private void logStarted(VectorRetrievalRequest request) {
        LOGGER.info(
                "Vector retrieval started queryHash={}, topK={}, filters={}",
                request.queryHash(),
                request.topK(),
                RetrievalSupport.filterCacheKey(request.filters())
        );
    }

    private void logCompleted(VectorRetrievalResult result) {
        if (result.status() == RetrievalStatus.EMPTY) {
            LOGGER.warn("Vector retrieval empty result queryHash={}, topK={}", result.queryHash(), result.requestedTopK());
        }
        LOGGER.info(
                "Vector retrieval completed queryHash={}, status={}, returnedChunks={}, embeddingLatencyMs={}, vectorDbLatencyMs={}, totalLatencyMs={}",
                result.queryHash(),
                result.status(),
                result.returnedChunkCount(),
                result.embeddingLatencyMs(),
                result.vectorDbLatencyMs(),
                result.totalLatencyMs()
        );
    }

    private void logFailed(VectorRetrievalResult failure, boolean embeddingFailure, boolean pgVectorFailure) {
        LOGGER.warn(
                "Vector retrieval failed queryHash={}, embeddingFailure={}, pgVectorFailure={}, reason={}",
                failure.queryHash(),
                embeddingFailure,
                pgVectorFailure,
                failure.failureReason()
        );
    }

    private record VectorRetrievalRequest(
            String query,
            String queryHash,
            int topK,
            RetrievalFilters filters
    ) {
    }

    private class VectorRetrievalTiming {
        private final Instant totalStarted;
        private long embeddingLatencyMs;
        private long vectorDbLatencyMs;
        private String embeddingModel;
        private int embeddingDimension;
        private boolean embeddingCompleted;

        VectorRetrievalTiming(Instant totalStarted) {
            this.totalStarted = totalStarted;
        }

        long totalLatencyMs() {
            return RetrievalSupport.elapsedMs(totalStarted);
        }
    }

    private class VectorEmbedding {
        private final EmbeddingVector embedding;

        VectorEmbedding(EmbeddingVector embedding) {
            this.embedding = embedding;
        }

        String model() {
            return embedding.model();
        }

        int dimension() {
            return embedding.dimension();
        }

        float[] values() {
            return embedding.values();
        }
    }
}
