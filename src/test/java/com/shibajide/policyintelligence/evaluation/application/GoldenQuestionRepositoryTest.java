package com.shibajide.policyintelligence.evaluation.application;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoldenQuestionRepositoryTest {

    @Test
    void loadsGoldenQuestionsFromResourceFile() {
        var repository = new GoldenQuestionRepository(new YAMLMapper());

        assertThat(repository.findAll())
                .hasSizeGreaterThanOrEqualTo(6)
                .extracting(GoldenQuestion::id)
                .contains("contractor-production-access");
    }
}
