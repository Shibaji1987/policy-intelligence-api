package com.acme.policyintelligence.advisor.queryexpansion;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Order(50)
@Component
public class MetadataAwareExpansion implements QueryExpansionStrategy {

    private final QueryExpansionProperties properties;

    public MetadataAwareExpansion(QueryExpansionProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<GeneratedQuery> expand(String baseQuery) {
        if (baseQuery == null || baseQuery.isBlank() || properties.metadataTerms().isEmpty()) {
            return List.of();
        }
        return List.of(new GeneratedQuery(
                baseQuery + " " + String.join(" ", properties.metadataTerms()),
                "Metadata-aware expansion for governed retrieval filters",
                "METADATA_AWARE",
                0.66
        ));
    }
}
