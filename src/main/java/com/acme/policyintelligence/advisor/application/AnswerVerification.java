package com.acme.policyintelligence.advisor.application;

import java.util.List;

public record AnswerVerification(
        boolean verified,
        String reason,
        List<String> unsupportedClaims,
        String confidence
) {
}
