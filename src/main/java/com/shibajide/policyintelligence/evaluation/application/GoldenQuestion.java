package com.shibajide.policyintelligence.evaluation.application;

import java.util.List;

public record GoldenQuestion(
        String id,
        String question,
        List<String> expectedSourceHints,
        String expectedAnswerHint,
        List<String> expectedChunkIds,
        List<String> expectedAnswerKeywords,
        List<String> expectedDocumentIds
) {
}
