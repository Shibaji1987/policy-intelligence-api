package com.acme.policyintelligence.advisor.application;

public record AnswerVerification(
        boolean verified,
        String reason
) {
}
