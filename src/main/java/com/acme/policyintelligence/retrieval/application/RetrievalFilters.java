package com.acme.policyintelligence.retrieval.application;

import java.util.Objects;
import java.util.stream.Stream;

public record RetrievalFilters(
        String tenantId,
        String department,
        String region,
        String documentType,
        String classification
) {
    public RetrievalFilters {
        tenantId = normalizeTenant(tenantId);
        department = blankToNull(department);
        region = blankToNull(region);
        documentType = blankToNull(documentType);
        classification = blankToNull(classification);
    }

    public static RetrievalFilters defaults() {
        return new RetrievalFilters("default", null, null, null, null);
    }

    public String cacheKey() {
        return Stream.of(tenantId, department, region, documentType, classification)
                .map(value -> Objects.toString(value, "*").toLowerCase())
                .reduce((left, right) -> left + "|" + right)
                .orElse("default|*|*|*|*");
    }

    private static String normalizeTenant(String value) {
        return value == null || value.isBlank() ? "default" : value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
