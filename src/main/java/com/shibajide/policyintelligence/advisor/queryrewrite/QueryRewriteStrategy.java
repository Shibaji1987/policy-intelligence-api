package com.shibajide.policyintelligence.advisor.queryrewrite;

public interface QueryRewriteStrategy {

    QueryRewriteResult rewrite(QueryRewriteRequest request);
}
