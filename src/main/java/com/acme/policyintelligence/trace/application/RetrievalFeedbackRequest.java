package com.acme.policyintelligence.trace.application;

public record RetrievalFeedbackRequest(
        String rating,
        String comment
) {
}
