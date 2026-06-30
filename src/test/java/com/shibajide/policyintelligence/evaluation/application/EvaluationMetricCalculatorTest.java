package com.shibajide.policyintelligence.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvaluationMetricCalculatorTest {

    private final EvaluationMetricCalculator calculator = new EvaluationMetricCalculator();

    @Test
    void sourceHintsCanScoreWithoutStableDocumentIds() {
        var wrong = chunk("Employee Production Customer Data Access Standard", "employees only");
        var right = chunk("Contractor Production Customer Data Access Standard", "contractor emergency support");

        assertThat(calculator.recallAt(List.of(wrong, right), List.of(), List.of("Contractor Production Customer Data Access Standard"), 5))
                .isEqualTo(1.0);
        assertThat(calculator.mrr(List.of(wrong, right), List.of(), List.of("Contractor Production Customer Data Access Standard")))
                .isEqualTo(0.5);
        assertThat(calculator.precisionAt(List.of(wrong, right), List.of(), List.of("Contractor Production Customer Data Access Standard"), 2))
                .isEqualTo(0.5);
    }

    private RetrievedChunk chunk(String title, String text) {
        return new RetrievedChunk(
                UUID.randomUUID(),
                title,
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                0,
                "section-1",
                "Section 1",
                text,
                0.9,
                0.8,
                0.85,
                1,
                1,
                0.1,
                "BOTH",
                null,
                0,
                null,
                "query",
                "HYBRID",
                text
        );
    }
}
