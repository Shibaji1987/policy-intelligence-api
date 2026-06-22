package com.acme.policyintelligence.advisor.queryexpansion;

import java.util.List;

public record QueryExpansionResult(
        String baseQuery,
        List<GeneratedQuery> generatedQueries,
        String expansionStrategy,
        long latencyMs,
        String status
) {
}
