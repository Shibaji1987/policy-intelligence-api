package com.acme.policyintelligence.advisor.queryexpansion;

import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class QueryExpansionService {

    private static final int MAX_QUERIES = 5;

    public QueryExpansionResult expand(QueryExpansionRequest request) {
        String baseQuery = request.baseQuery() == null ? "" : request.baseQuery().strip();
        var generated = new LinkedHashSet<GeneratedQuery>();
        generated.add(new GeneratedQuery(baseQuery, "direct semantic match"));

        String lower = baseQuery.toLowerCase(Locale.ROOT);
        if (lower.contains("contractor") || lower.contains("vendor") || lower.contains("third party")) {
            generated.add(new GeneratedQuery("third party access sensitive customer data approval", "policy synonym expansion"));
            generated.add(new GeneratedQuery("external worker production data policy restrictions", "role synonym expansion"));
        }
        if (lower.contains("approval") || lower.contains("access")) {
            generated.add(new GeneratedQuery("privileged access approval requirements evidence review", "approval and evidence expansion"));
        }
        if (lower.contains("production") || lower.contains("customer data")) {
            generated.add(new GeneratedQuery("production customer data security policy exception", "environment and data sensitivity expansion"));
        }

        List<GeneratedQuery> queries = generated.stream()
                .filter(query -> !query.query().isBlank())
                .limit(MAX_QUERIES)
                .toList();
        return new QueryExpansionResult(baseQuery, queries.isEmpty()
                ? List.of(new GeneratedQuery(baseQuery, "fallback original query"))
                : queries);
    }
}
