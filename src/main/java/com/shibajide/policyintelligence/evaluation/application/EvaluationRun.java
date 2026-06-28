package com.shibajide.policyintelligence.evaluation.application;

import java.util.List;
import java.util.UUID;

public record EvaluationRun(
        UUID runId,
        List<EvaluationResult> results,
        double averageLatencyMs,
        double averageTokenCount
) {
}
