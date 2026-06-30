package com.shibajide.policyintelligence.advisor.application;

import com.shibajide.policyintelligence.advisor.queryexpansion.QueryExpansionRequest;
import com.shibajide.policyintelligence.advisor.queryexpansion.QueryExpansionResult;
import com.shibajide.policyintelligence.advisor.queryexpansion.QueryExpansionService;
import com.shibajide.policyintelligence.advisor.queryrewrite.QueryRewriteRequest;
import com.shibajide.policyintelligence.advisor.queryrewrite.QueryRewriteResult;
import com.shibajide.policyintelligence.advisor.queryrewrite.QueryRewriteService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
class AdvisorQueryPlanner {

    private final QueryRewriteService queryRewriteService;
    private final QueryExpansionService queryExpansionService;

    AdvisorQueryPlanner(QueryRewriteService queryRewriteService, QueryExpansionService queryExpansionService) {
        this.queryRewriteService = queryRewriteService;
        this.queryExpansionService = queryExpansionService;
    }

    AdvisorQueryPlan plan(String question, AdvisorEventSink sink) {
        QueryRewriteResult rewrite = queryRewriteService.rewrite(new QueryRewriteRequest(question));
        QueryExpansionResult expansion = queryExpansionService.expand(new QueryExpansionRequest(rewrite.rewrittenQuery()));
        List<String> retrievalQueries = retrievalQueries(rewrite.rewrittenQuery(), expansion);

        sink.emit(AdvisorEvent.of(AdvisorStage.QUERY_REFINED, "Query refined", Map.of(
                "query", rewrite.rewrittenQuery(),
                "rewriteStrategy", rewrite.rewriteStrategy(),
                "rewriteLatencyMs", rewrite.latencyMs(),
                "retrievalQueries", retrievalQueries
        )));
        return new AdvisorQueryPlan(rewrite, expansion, retrievalQueries);
    }

    private List<String> retrievalQueries(String refinedQuery, QueryExpansionResult expansion) {
        List<String> retrievalQueries = expansion.generatedQueries().stream()
                .map(generated -> generated.query())
                .toList();
        if (!retrievalQueries.isEmpty()) {
            return retrievalQueries;
        }
        if (!refinedQuery.isBlank()) {
            return List.of(refinedQuery);
        }
        throw new IllegalArgumentException("Question must produce at least one retrieval query");
    }
}

