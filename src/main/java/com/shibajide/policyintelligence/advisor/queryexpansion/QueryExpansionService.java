package com.shibajide.policyintelligence.advisor.queryexpansion;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Service
public class QueryExpansionService {

    private final List<QueryExpansionStrategy> strategies;
    private final QueryExpansionProperties properties;

    public QueryExpansionService(
            List<QueryExpansionStrategy> strategies,
            QueryExpansionProperties properties
    ) {
        this.strategies = List.copyOf(strategies);
        this.properties = properties;
    }

    public QueryExpansionResult expand(QueryExpansionRequest request) {
        Instant started = Instant.now();
        String baseQuery = request.baseQuery() == null ? "" : request.baseQuery().strip();
        if (baseQuery.isBlank()) {
            return result(baseQuery, List.of(), started, "EMPTY_INPUT");
        }

        List<GeneratedQuery> generatedQueries = generatedQueries(baseQuery);
        return result(baseQuery, generatedQueries, started, generatedQueries.isEmpty() ? "EMPTY" : "COMPLETED");
    }

    private List<GeneratedQuery> generatedQueries(String baseQuery) {
        var deduped = new LinkedHashMap<String, GeneratedQuery>();
        add(deduped, baseQuery(baseQuery));
        strategies.forEach(strategy -> strategy.expand(baseQuery).forEach(generated -> add(deduped, generated)));
        return ranked(deduped);
    }

    private GeneratedQuery baseQuery(String baseQuery) {
        return new GeneratedQuery(
                baseQuery,
                "direct semantic match",
                "BASE_QUERY",
                0.95
        );
    }

    private List<GeneratedQuery> ranked(LinkedHashMap<String, GeneratedQuery> deduped) {
        return deduped.values().stream()
                .filter(query -> !query.query().isBlank())
                .sorted(Comparator.comparing(GeneratedQuery::confidence).reversed())
                .limit(properties.maxQueries())
                .toList();
    }

    private QueryExpansionResult result(
            String baseQuery,
            List<GeneratedQuery> generatedQueries,
            Instant started,
            String status
    ) {
        return new QueryExpansionResult(
                baseQuery,
                generatedQueries,
                "RULE_BASED_CONFIGURABLE",
                Duration.between(started, Instant.now()).toMillis(),
                status
        );
    }

    private void add(LinkedHashMap<String, GeneratedQuery> deduped, GeneratedQuery generatedQuery) {
        String key = normalize(generatedQuery.query());
        if (!key.isBlank()) {
            deduped.merge(
                    key,
                    generatedQuery,
                    (existing, candidate) -> candidate.confidence() > existing.confidence() ? candidate : existing
            );
        }
    }

    private String normalize(String query) {
        return query == null ? "" : query.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }
}
