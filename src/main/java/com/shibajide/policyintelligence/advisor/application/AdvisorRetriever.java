package com.shibajide.policyintelligence.advisor.application;

import com.shibajide.policyintelligence.retrieval.application.RetrievalFilters;
import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;
import com.shibajide.policyintelligence.retrieval.hybrid.HybridSearchResult;
import com.shibajide.policyintelligence.retrieval.hybrid.HybridRetrievalService;
import com.shibajide.policyintelligence.retrieval.infrastructure.VectorSearchRepository;
import com.shibajide.policyintelligence.retrieval.rerank.Reranker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
class AdvisorRetriever {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvisorRetriever.class);

    private final HybridRetrievalService hybridRetrievalService;
    private final Reranker reranker;
    private final VectorSearchRepository vectorSearchRepository;
    private final int retrievalTopK;
    private final int rerankedLimit;
    private final int parentChildSeedLimit;

    AdvisorRetriever(
            HybridRetrievalService hybridRetrievalService,
            Reranker reranker,
            VectorSearchRepository vectorSearchRepository,
            @Value("${app.advisor.retrieval-top-k:30}") int retrievalTopK,
            @Value("${app.advisor.reranked-limit:16}") int rerankedLimit,
            @Value("${app.advisor.parent-child-seed-limit:6}") int parentChildSeedLimit
    ) {
        this.hybridRetrievalService = hybridRetrievalService;
        this.reranker = reranker;
        this.vectorSearchRepository = vectorSearchRepository;
        this.retrievalTopK = Math.clamp(retrievalTopK, 5, 100);
        this.rerankedLimit = Math.clamp(rerankedLimit, 4, 50);
        this.parentChildSeedLimit = Math.clamp(parentChildSeedLimit, 0, 20);
    }

    RetrievalStep retrieve(String question, RetrievalFilters filters, AdvisorQueryPlan plan, AdvisorEventSink sink) {
        sink.emit(AdvisorEvent.of(AdvisorStage.VECTOR_SEARCH_STARTED, "Vector search started"));
        Instant started = Instant.now();
        HybridSearchResult retrieval = hybridRetrievalService.search(
                question,
                plan.refinedQuery(),
                plan.retrievalQueries(),
                retrievalTopK,
                filters
        );
        long latencyMs = elapsedMs(started);
        List<RetrievedChunk> chunks = rerankWithNeighbors(question, mergeRetrieved(retrieval.fusedResults()));

        LOGGER.info(
                "Advisor retrieval completed. plannedQueries={}, retrievedChunks={}",
                retrieval.expandedQueries().size(),
                chunks.size()
        );
        sink.emit(AdvisorEvent.of(AdvisorStage.CHUNKS_RETRIEVED, "Chunks retrieved", Map.of(
                "count", chunks.size(),
                "plannedQueries", retrieval.expandedQueries().size(),
                "latencyMs", latencyMs,
                "retrievalStatus", retrieval.status(),
                "retrievalStrategy", retrieval.retrievalStrategy(),
                "queryId", retrieval.queryId()
        )));
        return new RetrievalStep(retrieval, chunks, latencyMs);
    }

    private List<RetrievedChunk> rerankWithNeighbors(String question, List<RetrievedChunk> retrieved) {
        var seeds = retrieved.stream().limit(parentChildSeedLimit).toList();
        var neighbors = vectorSearchRepository.findActiveNeighbors(seeds);
        return reranker.rerank(question, mergeRetrieved(concat(retrieved, neighbors))).stream()
                .limit(rerankedLimit)
                .map(reranked -> reranked.toRetrievedChunk())
                .toList();
    }

    private List<RetrievedChunk> mergeRetrieved(List<RetrievedChunk> chunks) {
        var byId = new LinkedHashMap<java.util.UUID, RetrievedChunk>();
        for (RetrievedChunk chunk : chunks) {
            byId.merge(chunk.chunkId(), chunk, (left, right) -> left.combinedScore() >= right.combinedScore() ? left : right);
        }
        return List.copyOf(byId.values());
    }

    private List<RetrievedChunk> concat(List<RetrievedChunk> first, List<RetrievedChunk> second) {
        var combined = new java.util.ArrayList<RetrievedChunk>(first.size() + second.size());
        combined.addAll(first);
        combined.addAll(second);
        return List.copyOf(combined);
    }

    private long elapsedMs(Instant start) {
        return Duration.between(start, Instant.now()).toMillis();
    }
}

