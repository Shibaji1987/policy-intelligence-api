package com.shibajide.policyintelligence.retrieval.fusion;

import com.shibajide.policyintelligence.retrieval.application.RetrievalFilters;
import com.shibajide.policyintelligence.retrieval.application.RetrievalSupport;
import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;
import com.shibajide.policyintelligence.retrieval.hybrid.RetrievalSource;
import com.shibajide.policyintelligence.retrieval.hybrid.RetrieverExecutionResult;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ReciprocalRankFusionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReciprocalRankFusionService.class);

    private final FusionProperties properties;
    private final MeterRegistry meterRegistry;

    public ReciprocalRankFusionService(FusionProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    public FusionResult fuse(List<RetrieverExecutionResult> outcomes, int limit) {
        return fuse(FusionRequest.of(outcomes, limit));
    }

    public FusionResult fuse(FusionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Fusion request must not be null");
        }
        validateOutcomes(request.outcomes());
        validateLimit(request.limit());
        List<RetrievedChunk> vectorResults = request.outcomes().stream()
                .filter(outcome -> outcome.source() == RetrievalSource.VECTOR)
                .flatMap(outcome -> outcome.results().stream())
                .toList();
        List<RetrievedChunk> keywordResults = request.outcomes().stream()
                .filter(outcome -> outcome.source() == RetrievalSource.KEYWORD)
                .flatMap(outcome -> outcome.results().stream())
                .toList();
        return fuse(request.traceId(), vectorResults, keywordResults, request.limit(), request.filters());
    }

    public FusionResult fuse(List<RetrievedChunk> vectorResults, List<RetrievedChunk> keywordResults, int limit) {
        return fuse(UUID.randomUUID(), vectorResults, keywordResults, limit, null);
    }

    private FusionResult fuse(
            UUID traceId,
            List<RetrievedChunk> vectorResults,
            List<RetrievedChunk> keywordResults,
            int limit,
            RetrievalFilters filters
    ) {
        validateInputs(vectorResults, keywordResults, limit);
        long started = System.nanoTime();
        CandidateSet candidates = collectCandidates(vectorResults, keywordResults);
        List<RetrievedChunk> fused = fuseCandidates(candidates, limit);
        FusionTrace trace = trace(traceId, vectorResults, keywordResults, candidates, fused, limit, filters, started);

        recordMetrics(trace);
        logCompleted(trace);
        return new FusionResult(fused, trace);
    }

    private CandidateSet collectCandidates(List<RetrievedChunk> vectorResults, List<RetrievedChunk> keywordResults) {
        var candidates = new LinkedHashMap<UUID, FusionCandidate>();
        addVectorCandidates(candidates, vectorResults);
        int duplicateCount = addKeywordCandidates(candidates, keywordResults);
        return new CandidateSet(candidates, duplicateCount);
    }

    private void addVectorCandidates(Map<UUID, FusionCandidate> candidates, List<RetrievedChunk> chunks) {
        for (int index = 0; index < chunks.size(); index++) {
            RetrievedChunk chunk = chunks.get(index);
            candidates.put(chunk.chunkId(), new FusionCandidate(chunk, index + 1, null, chunk.similarityScore(), 0));
        }
    }

    private int addKeywordCandidates(Map<UUID, FusionCandidate> candidates, List<RetrievedChunk> chunks) {
        int duplicateCount = 0;
        for (int index = 0; index < chunks.size(); index++) {
            RetrievedChunk chunk = chunks.get(index);
            if (candidates.containsKey(chunk.chunkId())) {
                duplicateCount++;
            }
            candidates.merge(chunk.chunkId(), keywordCandidate(chunk, index + 1), this::mergeCandidate);
        }
        return duplicateCount;
    }

    private FusionCandidate keywordCandidate(RetrievedChunk chunk, int rank) {
        return new FusionCandidate(chunk, null, rank, 0, chunk.keywordScore());
    }

    private FusionCandidate mergeCandidate(FusionCandidate existing, FusionCandidate incoming) {
        return new FusionCandidate(
                existing.chunk(),
                existing.vectorRank(),
                incoming.keywordRank(),
                existing.vectorScore(),
                incoming.keywordScore()
        );
    }

    private List<RetrievedChunk> fuseCandidates(CandidateSet candidates, int limit) {
        return candidates.byChunkId().values().stream()
                .map(this::toFusedChunk)
                .sorted(fusionComparator())
                .limit(limit)
                .toList();
    }

    private RetrievedChunk toFusedChunk(FusionCandidate candidate) {
        double rrfScore = score(candidate.vectorRank()) + score(candidate.keywordRank());
        return candidate.chunk().withFusion(
                candidate.vectorRank(),
                candidate.keywordRank(),
                rrfScore,
                source(candidate)
        );
    }

    private String source(FusionCandidate candidate) {
        if (candidate.vectorRank() != null && candidate.keywordRank() != null) {
            return "BOTH";
        }
        return candidate.vectorRank() != null ? "VECTOR" : "KEYWORD";
    }

    private FusionTrace trace(
            UUID traceId,
            List<RetrievedChunk> vectorResults,
            List<RetrievedChunk> keywordResults,
            CandidateSet candidates,
            List<RetrievedChunk> fused,
            int limit,
            RetrievalFilters filters,
            long started
    ) {
        return new FusionTrace(
                traceId,
                "RECIPROCAL_RANK_FUSION",
                properties.rrfK(),
                limit,
                vectorResults.size(),
                keywordResults.size(),
                candidates.size(),
                candidates.duplicateCount(),
                fused.size(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                filters == null ? Map.of() : RetrievalSupport.filtersApplied(filters)
        );
    }

    private void validateOutcomes(List<RetrieverExecutionResult> outcomes) {
        if (outcomes == null) {
            throw new IllegalArgumentException("Retriever outcomes must not be null");
        }
        if (outcomes.stream().anyMatch(outcome -> outcome == null)) {
            throw new IllegalArgumentException("Retriever outcomes must not contain null values");
        }
    }

    private void validateInputs(List<RetrievedChunk> vectorResults, List<RetrievedChunk> keywordResults, int limit) {
        if (vectorResults == null) {
            throw new IllegalArgumentException("Vector results must not be null");
        }
        if (keywordResults == null) {
            throw new IllegalArgumentException("Keyword results must not be null");
        }
        validateLimit(limit);
    }

    private void validateLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Fusion limit must be positive");
        }
    }

    private Comparator<RetrievedChunk> fusionComparator() {
        return Comparator.comparingDouble(RetrievedChunk::rrfScore).reversed()
                .thenComparing(chunk -> "BOTH".equals(chunk.retrievalSource()) ? 0 : 1)
                .thenComparingInt(this::bestOriginalRank)
                .thenComparing(Comparator.comparingDouble(RetrievedChunk::similarityScore).reversed())
                .thenComparing(Comparator.comparingDouble(RetrievedChunk::keywordScore).reversed())
                .thenComparing(chunk -> chunk.chunkId().toString());
    }

    private int bestOriginalRank(RetrievedChunk chunk) {
        int vectorRank = chunk.vectorRank() == null ? Integer.MAX_VALUE : chunk.vectorRank();
        int keywordRank = chunk.keywordRank() == null ? Integer.MAX_VALUE : chunk.keywordRank();
        return Math.min(vectorRank, keywordRank);
    }

    private void recordMetrics(FusionTrace trace) {
        meterRegistry.timer("rag.fusion.latency").record(trace.latencyMs(), TimeUnit.MILLISECONDS);
        meterRegistry.counter("rag.fusion.input.count").increment(trace.vectorInputCount() + trace.keywordInputCount());
        meterRegistry.counter("rag.fusion.unique_candidate.count").increment(trace.uniqueCandidateCount());
        meterRegistry.counter("rag.fusion.duplicate.count").increment(trace.duplicateCount());
        meterRegistry.counter("rag.fusion.output.count").increment(trace.outputCount());
    }

    private void logCompleted(FusionTrace trace) {
        LOGGER.info(
                "Fusion completed traceId={}, algorithm={}, rrfK={}, vectorCount={}, keywordCount={}, uniqueCandidates={}, duplicates={}, outputCount={}, latencyMs={}",
                trace.traceId(),
                trace.algorithm(),
                trace.rrfK(),
                trace.vectorInputCount(),
                trace.keywordInputCount(),
                trace.uniqueCandidateCount(),
                trace.duplicateCount(),
                trace.outputCount(),
                trace.latencyMs()
        );
    }

    private double score(Integer rank) {
        return rank == null ? 0 : 1.0 / (properties.rrfK() + rank);
    }

    private record CandidateSet(
            LinkedHashMap<UUID, FusionCandidate> byChunkId,
            int duplicateCount
    ) {
        int size() {
            return byChunkId.size();
        }
    }
}
