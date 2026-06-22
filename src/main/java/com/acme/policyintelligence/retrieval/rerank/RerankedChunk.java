package com.acme.policyintelligence.retrieval.rerank;

import com.acme.policyintelligence.retrieval.application.RetrievedChunk;

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
