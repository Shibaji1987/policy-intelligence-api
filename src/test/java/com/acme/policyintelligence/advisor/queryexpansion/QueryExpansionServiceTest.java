package com.acme.policyintelligence.advisor.queryexpansion;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QueryExpansionServiceTest {

    @Test
    void returnsFallbackQueryForEmptyInput() {
        QueryExpansionService service = service(List.of());

        QueryExpansionResult result = service.expand(new QueryExpansionRequest(""));

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.generatedQueries()).hasSize(1);
        assertThat(result.generatedQueries().getFirst().query()).isEqualTo("policy");
    }

    @Test
    void limitsGeneratedQueriesToFive() {
        QueryExpansionService service = service(List.of(
                query -> List.of(
                        generated("one"),
                        generated("two"),
                        generated("three"),
                        generated("four"),
                        generated("five"),
                        generated("six")
                )
        ));

        QueryExpansionResult result = service.expand(new QueryExpansionRequest("contractor access"));

        assertThat(result.generatedQueries()).hasSize(5);
    }

    @Test
    void removesDuplicateQueriesWhilePreservingOrder() {
        QueryExpansionService service = service(List.of(
                query -> List.of(
                        generated("contractor access policy"),
                        generated("contractor   access, policy"),
                        generated("third party access policy")
                )
        ));

        QueryExpansionResult result = service.expand(new QueryExpansionRequest("contractor access policy"));

        assertThat(result.generatedQueries())
                .extracting(GeneratedQuery::query)
                .containsExactly("contractor access policy", "third party access policy");
    }

    private QueryExpansionService service(List<QueryExpansionStrategy> strategies) {
        return new QueryExpansionService(strategies, properties());
    }

    private QueryExpansionProperties properties() {
        return new QueryExpansionProperties(
                5,
                Map.of("contractor", List.of("third party", "vendor")),
                Map.of("access", List.of("approval requirements")),
                Map.of("mfa", "multi factor authentication"),
                List.of("department", "classification")
        );
    }

    private GeneratedQuery generated(String query) {
        return new GeneratedQuery(query, "test", "TEST", 0.5);
    }
}
