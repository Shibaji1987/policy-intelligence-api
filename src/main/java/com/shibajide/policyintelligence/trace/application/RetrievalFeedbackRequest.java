package com.shibajide.policyintelligence.trace.application;

public record RetrievalFeedbackRequest(
        String rating,
        String comment
) {
}
