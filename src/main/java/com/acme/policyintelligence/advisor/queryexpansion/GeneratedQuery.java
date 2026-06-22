package com.acme.policyintelligence.advisor.queryexpansion;

public record GeneratedQuery(
        String query,
        String reason,
        String expansionStrategy,
        double confidence
) {
}
