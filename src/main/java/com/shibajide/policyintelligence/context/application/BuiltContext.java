package com.shibajide.policyintelligence.context.application;

import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;

import java.util.List;

public record BuiltContext(
        String text,
        ContextMetrics metrics,
        List<RetrievedChunk> usedChunks,
        List<RetrievedChunk> discardedChunks,
        List<ContextChunkDecision> decisions
) {
}
