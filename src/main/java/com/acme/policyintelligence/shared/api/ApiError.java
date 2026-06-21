package com.acme.policyintelligence.shared.api;

import java.time.OffsetDateTime;
import java.util.Map;

public record ApiError(
        String code,
        String message,
        OffsetDateTime timestamp,
        Map<String, String> details
) {
}
