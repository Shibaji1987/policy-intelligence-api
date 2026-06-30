package com.shibajide.policyintelligence.advisor.application;

import com.shibajide.policyintelligence.advisor.queryexpansion.QueryExpansionResult;
import com.shibajide.policyintelligence.advisor.queryrewrite.QueryRewriteResult;

import java.util.List;

record AdvisorQueryPlan(
        QueryRewriteResult rewrite,
        QueryExpansionResult expansion,
        List<String> retrievalQueries
) {
    String refinedQuery() {
        return rewrite.rewrittenQuery();
    }
}

