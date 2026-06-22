package com.acme.policyintelligence.advisor.queryexpansion;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Order(20)
@Component
public class DomainSynonymExpansion implements QueryExpansionStrategy {

    private final QueryExpansionProperties properties;

    public DomainSynonymExpansion(QueryExpansionProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<GeneratedQuery> expand(String baseQuery) {
        String lower = baseQuery.toLowerCase(Locale.ROOT);
        var results = new ArrayList<GeneratedQuery>();
        properties.domainSynonyms().forEach((term, synonyms) -> {
            if (lower.contains(term.toLowerCase(Locale.ROOT))) {
                results.add(new GeneratedQuery(
                        String.join(" ", synonyms) + " " + baseQuery,
                        "Domain synonym expansion for " + term,
                        "DOMAIN_SYNONYM",
                        0.78
                ));
            }
        });
        return results;
    }
}
