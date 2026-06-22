package com.acme.policyintelligence.advisor.queryexpansion;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "app.query-expansion")
public record QueryExpansionProperties(
        int maxQueries,
        Map<String, List<String>> domainSynonyms,
        Map<String, List<String>> policyConcepts,
        Map<String, String> acronyms,
        List<String> metadataTerms
) {
    public QueryExpansionProperties {
        maxQueries = maxQueries <= 0 ? 5 : Math.min(maxQueries, 5);
        domainSynonyms = domainSynonyms == null ? Map.of() : Map.copyOf(domainSynonyms);
        policyConcepts = policyConcepts == null ? Map.of() : Map.copyOf(policyConcepts);
        acronyms = acronyms == null ? Map.of() : Map.copyOf(acronyms);
        metadataTerms = metadataTerms == null ? List.of() : List.copyOf(metadataTerms);
    }
}
