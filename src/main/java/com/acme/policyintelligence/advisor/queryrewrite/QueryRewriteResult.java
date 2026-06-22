package com.acme.policyintelligence.advisor.queryrewrite;

public record QueryRewriteResult(
        String originalQuestion,
        String rewrittenQuery,
        String rewriteStrategy,
        long latencyMs,
        String status
) {
}
