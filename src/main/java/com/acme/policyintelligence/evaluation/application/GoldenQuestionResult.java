package com.acme.policyintelligence.evaluation.application;

import java.util.List;
import java.util.UUID;

public record GoldenQuestionResult(
        String id,
        String question,
        UUID traceId,
        boolean expectedSourceMatched,
        List<String> matchedHints,
        int usedChunks,
        String qualityLabel,
        double qualityProbability
) {
}
