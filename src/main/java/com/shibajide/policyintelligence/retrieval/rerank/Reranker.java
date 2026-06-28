package com.shibajide.policyintelligence.retrieval.rerank;

import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;

import java.util.List;

public interface Reranker {

    List<RerankedChunk> rerank(String question, List<RetrievedChunk> chunks);
}
