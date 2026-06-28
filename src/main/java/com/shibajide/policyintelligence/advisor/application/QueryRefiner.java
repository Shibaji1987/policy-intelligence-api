package com.shibajide.policyintelligence.advisor.application;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class QueryRefiner {

    public String refine(String question) {
        if (question == null) {
            return "";
        }
        return expandDomainTerms(question.strip());
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

    private String expandDomainTerms(String value) {
        String expanded = value;
        expanded = expanded.replaceAll("(?i)\\bprod\\b", "production environment");
        expanded = expanded.replaceAll("(?i)\\bcontractors?\\b", "contractor third party vendor external worker");
        expanded = expanded.replaceAll("(?i)\\bpii\\b", "PII personally identifiable information");
        expanded = expanded.replaceAll("(?i)\\bphi\\b", "PHI protected health information");
        expanded = expanded.replaceAll("(?i)\\bsoc2\\b", "SOC2 security compliance audit");
        expanded = expanded.replaceAll("(?i)\\bpci[- ]?dss\\b", "PCI-DSS payment card data compliance");
        return expanded.replaceAll("\\s+", " ").strip();
    }
}
