package com.acme.policyintelligence.document.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentSummary(
        UUID id,
        String title,
        String tenantId,
        String department,
        String region,
        String documentType,
        String classification,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
