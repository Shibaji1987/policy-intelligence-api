package com.acme.policyintelligence.advisor.queryrewrite;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class QueryRewriteService {

    private final QueryRewritePromptBuilder promptBuilder;

    public QueryRewriteService(QueryRewritePromptBuilder promptBuilder) {
        this.promptBuilder = promptBuilder;
    }

    public QueryRewriteResult rewrite(QueryRewriteRequest request) {
        Instant started = Instant.now();
        String original = request.question() == null ? "" : request.question().strip();
        try {
            promptBuilder.build(original);
            String rewritten = ruleBasedRewrite(original);
            return new QueryRewriteResult(
                    original,
                    rewritten.isBlank() ? original : rewritten,
                    "RULE_BASED",
                    Duration.between(started, Instant.now()).toMillis(),
                    "COMPLETED"
            );
        } catch (RuntimeException exception) {
            return new QueryRewriteResult(
                    original,
                    original,
                    "FALLBACK_ORIGINAL",
                    Duration.between(started, Instant.now()).toMillis(),
                    "FAILED"
            );
        }
    }

    private String ruleBasedRewrite(String question) {
        String rewritten = question.toLowerCase(Locale.ROOT)
                .replaceAll("\\bprod\\b", "production")
                .replaceAll("\\bcontractors?\\b", "contractor third party vendor external worker")
                .replaceAll("\\baccess\\b", "access approval requirements")
                .replaceAll("\\bcustomer data\\b", "customer data sensitive production data")
                .replaceAll("[^a-z0-9\\s-]", " ")
                .replaceAll("\\b(can|could|should|would|what|when|where|why|how|do|does|is|are|the|a|an)\\b", " ")
                .replaceAll("\\s+", " ")
                .strip();
        return rewritten.isBlank() ? question : rewritten + " policy";
    }
}
