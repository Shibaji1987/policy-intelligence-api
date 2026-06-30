package com.shibajide.policyintelligence.evaluation.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.List;

@Repository
public class GoldenQuestionRepository {

    private final List<GoldenQuestion> goldenQuestions;

    public GoldenQuestionRepository() {
        this.goldenQuestions = load(yamlMapper());
    }

    public List<GoldenQuestion> findAll() {
        return goldenQuestions;
    }

    private List<GoldenQuestion> load(YAMLMapper yamlMapper) {
        try (var input = new ClassPathResource("evaluation/golden-questions.yaml").getInputStream()) {
            return List.copyOf(yamlMapper.readValue(input, new TypeReference<List<GoldenQuestion>>() {
            }));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load golden questions", exception);
        }
    }

    private YAMLMapper yamlMapper() {
        return YAMLMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .build();
    }
}
