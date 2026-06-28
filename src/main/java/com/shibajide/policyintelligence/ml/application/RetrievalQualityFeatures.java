package com.shibajide.policyintelligence.ml.application;

public record RetrievalQualityFeatures(
        double topSimilarityScore,
        double avgTop5Similarity,
        int documentDiversity,
        int usedChunkCount,
        int questionLength
) {
}
