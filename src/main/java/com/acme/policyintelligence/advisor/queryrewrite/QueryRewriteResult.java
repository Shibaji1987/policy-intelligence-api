package com.acme.policyintelligence.advisor.queryrewrite;

public record QueryRewriteResult(
        String originalQuestion,
        String rewrittenQuery,
        String rewriteStrategy,
        double confidence,
        boolean llmAttempted,
        String fallbackReason,
        long latencyMs,
        String status
) {
}
