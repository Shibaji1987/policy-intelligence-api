package com.shibajide.policyintelligence.advisor.queryexpansion;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Order(40)
@Component
public class AcronymExpansion implements QueryExpansionStrategy {

    private final QueryExpansionProperties properties;

    public AcronymExpansion(QueryExpansionProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<GeneratedQuery> expand(String baseQuery) {
        String lower = baseQuery.toLowerCase(Locale.ROOT);
        var results = new ArrayList<GeneratedQuery>();
        properties.acronyms().forEach((acronym, expansion) -> {
            if (lower.matches(".*\\b" + acronym.toLowerCase(Locale.ROOT) + "\\b.*")) {
                results.add(new GeneratedQuery(
                        baseQuery + " " + expansion,
                        "Acronym expansion for " + acronym,
                        "ACRONYM",
                        0.82
                ));
            }
        });
        return results;
    }
}
