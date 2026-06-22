package com.acme.policyintelligence.advisor.queryrewrite;

import org.springframework.stereotype.Component;

@Component
public class QueryRewritePromptBuilder {

    public String build(String question) {
        return """
                Rewrite this enterprise policy question into a concise retrieval query.
                Preserve approval, evidence, risk, role, document type, and data classification terms.
                Question: %s
                """.formatted(question == null ? "" : question.strip());
    }
}
