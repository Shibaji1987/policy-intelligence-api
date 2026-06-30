package com.shibajide.policyintelligence.retrieval.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

public final class RetrievalSupport {

    private RetrievalSupport() {
    }

    public static String requireQuery(String query, String retrievalName) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException(retrievalName + " query must not be blank");
        }
        return query.strip();
    }

    public static int requirePositiveTopK(int topK, int maxTopK) {
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }
        return Math.min(topK, maxTopK);
    }

    public static String queryHash(String query) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(query.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public static Map<String, String> filtersApplied(RetrievalFilters filters) {
        return Map.of(
                "tenantId", value(filters.tenantId()),
                "department", value(filters.department()),
                "region", value(filters.region()),
                "documentType", value(filters.documentType()),
                "classification", value(filters.classification())
        );
    }

    public static String filterCacheKey(RetrievalFilters filters) {
        return filters.cacheKey();
    }

    public static String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    public static long elapsedMs(Instant started) {
        return Duration.between(started, Instant.now()).toMillis();
    }

    private static String value(String value) {
        return value == null ? "*" : value;
    }
}
