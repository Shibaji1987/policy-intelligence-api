package com.shibajide.policyintelligence.evaluation.application;

import java.util.UUID;

public record EvaluationResult(
        String questionId,
        UUID traceId,
        double recallAt5,
        double recallAt10,
        double mrr,
        double precisionAt5,
        double citationAccuracy,
        double answerGroundedness,
        double faithfulness,
        long latencyMs,
        int tokenCount
) {
}
