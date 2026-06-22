package com.acme.policyintelligence.retrieval.fusion;

import com.acme.policyintelligence.retrieval.application.RetrievedChunk;

public record FusionCandidate(
        RetrievedChunk chunk,
        Integer vectorRank,
        Integer keywordRank,
        double vectorScore,
        double keywordScore
) {
}
