package com.shibajide.policyintelligence.retrieval.fusion;

import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;

public record FusionCandidate(
        RetrievedChunk chunk,
        Integer vectorRank,
        Integer keywordRank,
        double vectorScore,
        double keywordScore
) {
}
