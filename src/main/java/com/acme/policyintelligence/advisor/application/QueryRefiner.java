package com.acme.policyintelligence.advisor.application;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class QueryRefiner {

    public String refine(String question) {
        return question == null ? "" : question.strip();
    }

    public QueryPlan plan(String question) {
        String refined = refine(question);
        Set<String> queries = new LinkedHashSet<>();
        if (!refined.isBlank()) {
            queries.add(refined);
            queries.add(removeQuestionWords(refined));
            queries.add(extractPolicyTerms(refined));
        }
        return new QueryPlan(refined, new ArrayList<>(queries).stream()
                .filter(query -> !query.isBlank())
                .limit(3)
                .toList());
    }

    private String removeQuestionWords(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("\\b(can|could|should|would|what|when|where|why|how|do|does|is|are|the|a|an)\\b", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private String extractPolicyTerms(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", " ")
                .replaceAll("\\b(access|policy|requirement|allowed|approval|review|data|security|risk)\\b", "$0 ")
                .replaceAll("\\s+", " ")
                .strip();
    }
}
