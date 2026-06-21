package com.acme.policyintelligence.ml.api;

public record MlHealthStatus(
        boolean enabled,
        boolean reachable,
        boolean modelLoaded,
        String baseUrl,
        String message
) {
}
