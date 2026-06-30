package com.shibajide.policyintelligence.evaluation.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoldenQuestionRepositoryTest {

    @Test
    void loadsGoldenQuestionsFromResourceFile() {
        var repository = new GoldenQuestionRepository();

        assertThat(repository.findAll())
                .hasSizeGreaterThanOrEqualTo(6)
                .extracting(GoldenQuestion::id)
                .contains("contractor-production-access");
    }
}
