package com.acme.policyintelligence.ml.application;

public record RetrievalQualityPrediction(
        String label,
        double probability,
        String modelVersion
) {

    public static RetrievalQualityPrediction unavailable() {
        return new RetrievalQualityPrediction("UNKNOWN", 0.0, "unavailable");
    }
}
