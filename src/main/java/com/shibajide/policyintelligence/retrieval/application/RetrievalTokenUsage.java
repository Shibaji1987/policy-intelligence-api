package com.shibajide.policyintelligence.retrieval.application;

import com.shibajide.policyintelligence.billing.application.BillingEstimate;

public record RetrievalTokenUsage(
        int queryTokens,
        int returnedChunkTokens,
        int returnedExcerptTokens,
        int totalEstimatedTokens,
        String tokenizationStrategy,
        BillingEstimate billingEstimate
) {
}
