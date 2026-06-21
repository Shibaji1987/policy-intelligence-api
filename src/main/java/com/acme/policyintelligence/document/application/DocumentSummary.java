package com.acme.policyintelligence.document.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentSummary(
        UUID id,
        String title,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
