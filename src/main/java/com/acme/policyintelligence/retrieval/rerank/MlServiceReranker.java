package com.acme.policyintelligence.retrieval.rerank;

import com.acme.policyintelligence.retrieval.application.RetrievedChunk;

import java.util.List;

public class MlServiceReranker implements Reranker {

    @Override
    public List<RerankedChunk> rerank(String question, List<RetrievedChunk> chunks) {
        throw new UnsupportedOperationException("ML service cross-encoder reranking is a future extension point.");
    }
}
