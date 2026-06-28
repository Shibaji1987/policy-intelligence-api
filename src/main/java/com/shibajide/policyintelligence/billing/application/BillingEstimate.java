package com.shibajide.policyintelligence.billing.application;

import java.math.BigDecimal;

public record BillingEstimate(
        String strategy,
        String currency,
        int billableInputTokens,
        int billableOutputTokens,
        BigDecimal inputCost,
        BigDecimal outputCost,
        BigDecimal totalCost,
        BigDecimal inputRatePerMillionTokens,
        BigDecimal outputRatePerMillionTokens
) {
}
