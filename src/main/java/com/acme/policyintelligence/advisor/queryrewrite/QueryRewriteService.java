package com.acme.policyintelligence.advisor.queryrewrite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class QueryRewriteService {

    private final RuleBasedQueryRewriteStrategy ruleBasedStrategy;
    private final LlmQueryRewriteStrategy llmStrategy;
    private final double llmConfidenceThreshold;

    public QueryRewriteService(
            RuleBasedQueryRewriteStrategy ruleBasedStrategy,
            LlmQueryRewriteStrategy llmStrategy,
            @Value("${app.query-rewrite.llm-confidence-threshold:0.75}") double llmConfidenceThreshold
    ) {
        this.ruleBasedStrategy = ruleBasedStrategy;
        this.llmStrategy = llmStrategy;
        this.llmConfidenceThreshold = llmConfidenceThreshold;
    }

    public QueryRewriteResult rewrite(QueryRewriteRequest request) {
        QueryRewriteResult ruleBased = ruleBasedStrategy.rewrite(request);
        if (ruleBased.confidence() >= llmConfidenceThreshold) {
            return ruleBased;
        }

        QueryRewriteResult llm = llmStrategy.rewrite(request);
        if ("COMPLETED".equals(llm.status()) && !llm.rewrittenQuery().isBlank()) {
            return llm;
        }

        return new QueryRewriteResult(
                ruleBased.originalQuestion(),
                ruleBased.rewrittenQuery(),
                "RULE_BASED_WITH_LLM_FALLBACK",
                ruleBased.confidence(),
                true,
                llm.fallbackReason(),
                ruleBased.latencyMs() + llm.latencyMs(),
                "COMPLETED"
        );
    }
}
