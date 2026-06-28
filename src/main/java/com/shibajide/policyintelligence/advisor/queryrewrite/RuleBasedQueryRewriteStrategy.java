package com.shibajide.policyintelligence.advisor.queryrewrite;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Component
public class RuleBasedQueryRewriteStrategy implements QueryRewriteStrategy {

    private static final Set<String> VAGUE_REFERENCES = Set.of("it", "this", "that", "they", "them", "those");

    @Override
    public QueryRewriteResult rewrite(QueryRewriteRequest request) {
        Instant started = Instant.now();
        String original = request.question() == null ? "" : request.question().strip();
        String rewritten = rewriteRules(original);
        return new QueryRewriteResult(
                original,
                rewritten.isBlank() ? original : rewritten,
                "RULE_BASED",
                confidence(original, rewritten),
                false,
                null,
                Duration.between(started, Instant.now()).toMillis(),
                "COMPLETED"
        );
    }

    private String rewriteRules(String question) {
        String rewritten = question.toLowerCase(Locale.ROOT)
                .replaceAll("\\bprod\\b", "production")
                .replaceAll("\\bpii\\b", "personally identifiable information")
                .replaceAll("\\bmfa\\b", "multi factor authentication")
                .replaceAll("\\bcontractors?\\b", "contractor")
                .replaceAll("\\bcustomer data\\b", "customer data production data")
                .replaceAll("\\baccess\\b", "access approval requirements")
                .replaceAll("[^a-z0-9\\s-]", " ")
                .replaceAll("\\b(can|could|should|would|what|when|where|why|how|do|does|is|are|the|a|an)\\b", " ")
                .replaceAll("\\s+", " ")
                .strip();
        return rewritten.isBlank() ? question : rewritten + " policy";
    }

    private double confidence(String original, String rewritten) {
        if (original.isBlank() || rewritten.isBlank()) {
            return 0;
        }
        double confidence = 0.82;
        String lower = original.toLowerCase(Locale.ROOT);
        long wordCount = lower.split("\\s+").length;
        if (wordCount > 18) {
            confidence -= 0.18;
        }
        if (lower.contains(" and ") || lower.contains(";")) {
            confidence -= 0.12;
        }
        for (String vagueReference : VAGUE_REFERENCES) {
            if (lower.matches(".*\\b" + vagueReference + "\\b.*")) {
                confidence -= 0.22;
                break;
            }
        }
        if (rewritten.equalsIgnoreCase(original)) {
            confidence -= 0.12;
        }
        return Math.max(0.05, Math.min(0.98, confidence));
    }
}
