package com.shibajide.policyintelligence.advisor.application;

import java.util.List;

public record QueryPlan(
        String refinedQuery,
        List<String> retrievalQueries
) {
}
