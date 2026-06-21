package com.acme.policyintelligence.context.application;

import com.acme.policyintelligence.retrieval.application.RetrievedChunk;

import java.util.List;

public record BuiltContext(
        String text,
        ContextMetrics metrics,
        List<RetrievedChunk> usedChunks,
        List<RetrievedChunk> discardedChunks,
        List<ContextChunkDecision> decisions
) {
}
