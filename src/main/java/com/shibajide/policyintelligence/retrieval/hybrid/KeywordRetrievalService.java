package com.shibajide.policyintelligence.retrieval.hybrid;

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
        KeywordRetrievalExecution execution = prepare(request);
        Instant started = Instant.now();

        logStarted(execution);

        try {
            List<RetrievedChunk> chunks = search(execution);
            KeywordRetrievalResult result = success(execution, chunks, started);
            recordMetrics(result);
            logCompleted(result);
            return result;
        } catch (RuntimeException exception) {
            KeywordRetrievalResult failure = failure(execution, started, exception);
            recordMetrics(failure);
            logFailed(failure);
            return failure;
        }
    }

    private KeywordRetrievalExecution prepare(KeywordRetrievalRequest request) {
        String query = RetrievalSupport.requireQuery(request.query(), "Keyword retrieval");
        return new KeywordRetrievalExecution(
                request,
                query,
                RetrievalSupport.queryHash(query),
                RetrievalSupport.requirePositiveTopK(request.topK(), MAX_TOP_K),
                request.filters()
        );
    }

    private List<RetrievedChunk> search(KeywordRetrievalExecution execution) {
        return repository.keywordSearch(
                execution.query(),
                execution.topK(),
                execution.filters()
        );
    }

    private KeywordRetrievalResult success(
            KeywordRetrievalExecution execution,
            List<RetrievedChunk> chunks,
            Instant started
    ) {
        return KeywordRetrievalResult.success(
                execution.traceId(),
                chunks,
                execution.queryHash(),
                execution.searchMode(),
                execution.topK(),
                execution.filters(),
                RetrievalSupport.elapsedMs(started)
        );
    }

    private KeywordRetrievalResult failure(
            KeywordRetrievalExecution execution,
            Instant started,
            RuntimeException exception
    ) {
        return KeywordRetrievalResult.failure(
                execution.traceId(),
                execution.queryHash(),
                execution.searchMode(),
                execution.topK(),
                execution.filters(),
                RetrievalSupport.elapsedMs(started),
                RetrievalSupport.safeMessage(exception)
        );
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

    private void logStarted(KeywordRetrievalExecution execution) {
        LOGGER.info(
                "Keyword retrieval started traceId={}, queryHash={}, topK={}, filters={}, searchMode={}",
                execution.traceId(),
                execution.queryHash(),
                execution.topK(),
                execution.filters().cacheKey(),
                execution.searchMode()
        );
    }

    private void logCompleted(KeywordRetrievalResult result) {
        if (result.status() == RetrievalStatus.EMPTY) {
            LOGGER.warn(
                    "Keyword retrieval empty result traceId={}, queryHash={}, topK={}",
                    result.traceId(),
                    result.queryHash(),
                    result.requestedTopK()
            );
        }
        LOGGER.info(
                "Keyword retrieval completed traceId={}, queryHash={}, status={}, resultCount={}, latencyMs={}, searchMode={}, rankingFunction={}",
                result.traceId(),
                result.queryHash(),
                result.status(),
                result.returnedChunkCount(),
                result.latencyMs(),
                result.searchMode(),
                result.rankingFunction()
        );
    }

    private void logFailed(KeywordRetrievalResult failure) {
        LOGGER.warn(
                "Keyword retrieval failed traceId={}, queryHash={}, searchMode={}, reason={}",
                failure.traceId(),
                failure.queryHash(),
                failure.searchMode(),
                failure.failureReason()
        );
    }

    private record KeywordRetrievalExecution(
            KeywordRetrievalRequest request,
            String query,
            String queryHash,
            int topK,
            RetrievalFilters filters
    ) {
        java.util.UUID traceId() {
            return request.traceId();
        }

        String searchMode() {
            return request.searchMode();
        }
    }
}
