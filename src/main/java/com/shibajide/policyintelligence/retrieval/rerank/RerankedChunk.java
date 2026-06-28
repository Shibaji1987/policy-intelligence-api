package com.shibajide.policyintelligence.retrieval.rerank;

import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;

public record RerankedChunk(
        RetrievedChunk chunk,
        double rrfScore,
        double rerankScore,
        int rerankRank,
        String rerankReason
) {
    public RetrievedChunk toRetrievedChunk() {
        return chunk.withRerank(rerankRank, rerankScore, rerankReason);
    }
}
