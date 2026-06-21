package com.acme.policyintelligence.evaluation.application;

import java.util.List;

public record GoldenQuestion(
        String id,
        String question,
        List<String> expectedSourceHints,
        String expectedAnswerHint
) {
}
