package com.acme.policyintelligence.retrieval.rerank;

import com.acme.policyintelligence.retrieval.application.RetrievedChunk;

import java.util.List;

public interface Reranker {

    List<RerankedChunk> rerank(String question, List<RetrievedChunk> chunks);
}
