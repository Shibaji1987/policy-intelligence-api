package com.acme.policyintelligence.advisor.application;

public record AdvisorRequest(
        String question,
        String tenantId,
        String department,
        String region,
        String documentType,
        String classification
) {
}
