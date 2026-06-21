package com.acme.policyintelligence.advisor.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

public record AdvisorEvent(
        AdvisorStage stage,
        String message,
        OffsetDateTime timestamp,
        Map<String, Object> details
) {

    public static AdvisorEvent of(AdvisorStage stage, String message) {
        return new AdvisorEvent(stage, message, OffsetDateTime.now(ZoneOffset.UTC), Map.of());
    }

    public static AdvisorEvent of(AdvisorStage stage, String message, Map<String, Object> details) {
        return new AdvisorEvent(stage, message, OffsetDateTime.now(ZoneOffset.UTC), details);
    }
}
