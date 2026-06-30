package com.shibajide.policyintelligence.retrieval.rerank;

import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;

import java.util.List;

public class MlServiceReranker implements Reranker {

    @Override
    public List<RerankedChunk> rerank(String question, List<RetrievedChunk> chunks) {
        return new HeuristicReranker().rerank(question, chunks).stream()
                .map(chunk -> new RerankedChunk(
                        chunk.chunk(),
                        chunk.rrfScore(),
                        chunk.rerankScore(),
                        chunk.rerankRank(),
                        "ML service reranker unavailable; heuristic fallback used"
                ))
                .toList();
    }
}
