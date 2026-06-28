package com.shibajide.policyintelligence.advisor.queryexpansion;

public record GeneratedQuery(
        String query,
        String reason,
        String expansionStrategy,
        double confidence
) {
}
