package com.shibajide.policyintelligence.retrieval.rerank;

import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;

import java.util.List;
import java.util.stream.IntStream;

public class NoOpReranker implements Reranker {

    @Override
    public List<RerankedChunk> rerank(String question, List<RetrievedChunk> chunks) {
        return IntStream.range(0, chunks.size())
                .mapToObj(index -> new RerankedChunk(chunks.get(index), chunks.get(index).rrfScore(), chunks.get(index).rrfScore(), index + 1, "No-op preserves RRF order"))
                .toList();
    }
}
