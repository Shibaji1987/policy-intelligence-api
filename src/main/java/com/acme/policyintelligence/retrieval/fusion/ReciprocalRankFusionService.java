package com.acme.policyintelligence.retrieval.fusion;

import com.acme.policyintelligence.retrieval.application.RetrievedChunk;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
public class ReciprocalRankFusionService {

    private static final int RRF_K = 60;

    public FusionResult fuse(List<RetrievedChunk> vectorResults, List<RetrievedChunk> keywordResults, int limit) {
        var candidates = new LinkedHashMap<UUID, FusionCandidate>();
        for (int index = 0; index < vectorResults.size(); index++) {
            RetrievedChunk chunk = vectorResults.get(index);
            int rank = index + 1;
            candidates.put(chunk.chunkId(), new FusionCandidate(chunk, rank, null, chunk.similarityScore(), 0));
        }
        for (int index = 0; index < keywordResults.size(); index++) {
            RetrievedChunk keywordChunk = keywordResults.get(index);
            int rank = index + 1;
            candidates.merge(
                    keywordChunk.chunkId(),
                    new FusionCandidate(keywordChunk, null, rank, 0, keywordChunk.keywordScore()),
                    (existing, incoming) -> new FusionCandidate(
                            existing.chunk(),
                            existing.vectorRank(),
                            incoming.keywordRank(),
                            existing.vectorScore(),
                            incoming.keywordScore()
                    )
            );
        }

        List<RetrievedChunk> fused = candidates.values().stream()
                .map(candidate -> {
                    double rrfScore = score(candidate.vectorRank()) + score(candidate.keywordRank());
                    String source = candidate.vectorRank() != null && candidate.keywordRank() != null
                            ? "BOTH"
                            : candidate.vectorRank() != null ? "VECTOR" : "KEYWORD";
                    return candidate.chunk().withFusion(candidate.vectorRank(), candidate.keywordRank(), rrfScore, source);
                })
                .sorted(Comparator.comparingDouble(RetrievedChunk::rrfScore).reversed())
                .limit(limit)
                .toList();
        return new FusionResult(fused);
    }

    private double score(Integer rank) {
        return rank == null ? 0 : 1.0 / (RRF_K + rank);
    }
}
