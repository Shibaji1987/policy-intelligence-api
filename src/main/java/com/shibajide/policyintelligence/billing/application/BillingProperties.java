package com.shibajide.policyintelligence.billing.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "app.billing")
public record BillingProperties(
        String strategy,
        String currency,
        BigDecimal inputUsdPerMillionTokens,
        BigDecimal outputUsdPerMillionTokens
) {

    public BillingProperties {
        strategy = strategy == null || strategy.isBlank() ? "ESTIMATED_INPUT_ONLY" : strategy;
        currency = currency == null || currency.isBlank() ? "USD" : currency;
        inputUsdPerMillionTokens = inputUsdPerMillionTokens == null ? BigDecimal.ZERO : inputUsdPerMillionTokens;
        outputUsdPerMillionTokens = outputUsdPerMillionTokens == null ? BigDecimal.ZERO : outputUsdPerMillionTokens;
    }
}
