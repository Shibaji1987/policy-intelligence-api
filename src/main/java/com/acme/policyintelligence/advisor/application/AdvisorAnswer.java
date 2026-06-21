package com.acme.policyintelligence.advisor.application;

import com.acme.policyintelligence.context.application.ContextMetrics;
import com.acme.policyintelligence.ml.application.RetrievalQualityPrediction;
import com.acme.policyintelligence.retrieval.application.RetrievedChunk;

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
