package com.shibajide.policyintelligence.advisor.application;

import com.shibajide.policyintelligence.context.application.BuiltContext;
import com.shibajide.policyintelligence.ml.application.RetrievalQualityPrediction;
import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;
import com.shibajide.policyintelligence.retrieval.hybrid.HybridSearchResult;

import java.util.List;
import java.util.UUID;

record RetrievalStep(
        HybridSearchResult result,
        List<RetrievedChunk> chunks,
        long latencyMs
) {
}

record ContextStep(
        BuiltContext context,
        long latencyMs
) {
}

record AnswerStep(
        String answer,
        AnswerVerification verification,
        String generatorName,
        long latencyMs
) {
}

record PredictionStep(
        RetrievalQualityPrediction prediction,
        long latencyMs
) {
}

record TraceStep(
        UUID traceId
) {
}

