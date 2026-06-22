package com.acme.policyintelligence.advisor.queryrewrite;

public interface QueryRewriteStrategy {

    QueryRewriteResult rewrite(QueryRewriteRequest request);
}
