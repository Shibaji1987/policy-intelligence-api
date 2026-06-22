package com.acme.policyintelligence.advisor.queryexpansion;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Order(30)
@Component
public class PolicyConceptExpansion implements QueryExpansionStrategy {

    private final QueryExpansionProperties properties;

    public PolicyConceptExpansion(QueryExpansionProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<GeneratedQuery> expand(String baseQuery) {
        String lower = baseQuery.toLowerCase(Locale.ROOT);
        var results = new ArrayList<GeneratedQuery>();
        properties.policyConcepts().forEach((term, concepts) -> {
            String normalizedTerm = term.toLowerCase(Locale.ROOT).replace('-', ' ');
            if (lower.contains(normalizedTerm)) {
                results.add(new GeneratedQuery(
                        baseQuery + " " + String.join(" ", concepts),
                        "Policy concept expansion for " + term,
                        "POLICY_CONCEPT",
                        0.74
                ));
            }
        });
        return results;
    }
}
