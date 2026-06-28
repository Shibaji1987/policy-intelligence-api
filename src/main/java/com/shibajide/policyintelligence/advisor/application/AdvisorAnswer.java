package com.shibajide.policyintelligence.advisor.application;

import com.shibajide.policyintelligence.context.application.ContextMetrics;
import com.shibajide.policyintelligence.ml.application.RetrievalQualityPrediction;
import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;

import java.util.List;
import java.util.UUID;

public record AdvisorAnswer(
        UUID traceId,
        String question,
        String refinedQuery,
        String answer,
        ContextMetrics contextMetrics,
        RetrievalQualityPrediction qualityPrediction,
        List<RetrievedChunk> sources
) {
}
