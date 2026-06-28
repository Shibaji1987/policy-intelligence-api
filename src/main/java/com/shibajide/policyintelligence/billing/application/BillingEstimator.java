package com.shibajide.policyintelligence.billing.application;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class BillingEstimator {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

    private final BillingProperties properties;

    public BillingEstimator(BillingProperties properties) {
        this.properties = properties;
    }

    public BillingEstimate estimate(int inputTokens, int outputTokens) {
        BigDecimal inputCost = cost(inputTokens, properties.inputUsdPerMillionTokens());
        BigDecimal outputCost = cost(outputTokens, properties.outputUsdPerMillionTokens());
        return new BillingEstimate(
                properties.strategy(),
                properties.currency(),
                inputTokens,
                outputTokens,
                inputCost,
                outputCost,
                inputCost.add(outputCost),
                properties.inputUsdPerMillionTokens(),
                properties.outputUsdPerMillionTokens()
        );
    }

    private BigDecimal cost(int tokens, BigDecimal ratePerMillionTokens) {
        if (tokens <= 0 || BigDecimal.ZERO.compareTo(ratePerMillionTokens) == 0) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(tokens)
                .multiply(ratePerMillionTokens)
                .divide(ONE_MILLION, 8, RoundingMode.HALF_UP);
    }
}
