package com.shibajide.policyintelligence.evaluation.application;

import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;

import java.util.List;
import java.util.Locale;

public class EvaluationMetricCalculator {

    public double precisionAt(List<RetrievedChunk> chunks, List<String> expectedDocumentIds, int k) {
        return precisionAt(chunks, expectedDocumentIds, List.of(), k);
    }

    public double precisionAt(List<RetrievedChunk> chunks, List<String> expectedDocumentIds, List<String> expectedSourceHints, int k) {
        if (chunks.isEmpty() || expectedDocumentIds.isEmpty()) {
            if (expectedSourceHints.isEmpty()) {
                return 0;
            }
        }
        if (chunks.isEmpty()) {
            return 0;
        }
        long matches = chunks.stream()
                .limit(k)
                .filter(chunk -> matches(chunk, expectedDocumentIds, expectedSourceHints))
                .count();
        return (double) matches / Math.min(k, chunks.size());
    }

    public double recallAt(List<RetrievedChunk> chunks, List<String> expectedDocumentIds, int k) {
        return recallAt(chunks, expectedDocumentIds, List.of(), k);
    }

    public double recallAt(List<RetrievedChunk> chunks, List<String> expectedDocumentIds, List<String> expectedSourceHints, int k) {
        int expectedCount = expectedCount(expectedDocumentIds, expectedSourceHints);
        if (expectedCount == 0) {
            return 0;
        }
        long matches = expectedDocumentIds.isEmpty()
                ? expectedSourceHints.stream().filter(hint -> chunks.stream().limit(k).anyMatch(chunk -> matchesHint(chunk, hint))).count()
                : chunks.stream()
                        .limit(k)
                        .map(chunk -> chunk.documentId().toString())
                        .distinct()
                        .filter(expectedDocumentIds::contains)
                        .count();
        return (double) matches / expectedCount;
    }

    public double mrr(List<RetrievedChunk> chunks, List<String> expectedDocumentIds) {
        return mrr(chunks, expectedDocumentIds, List.of());
    }

    public double mrr(List<RetrievedChunk> chunks, List<String> expectedDocumentIds, List<String> expectedSourceHints) {
        for (int index = 0; index < chunks.size(); index++) {
            if (matches(chunks.get(index), expectedDocumentIds, expectedSourceHints)) {
                return 1.0 / (index + 1);
            }
        }
        return 0;
    }

    public boolean matches(RetrievedChunk chunk, List<String> expectedDocumentIds, List<String> expectedSourceHints) {
        if (expectedDocumentIds.contains(chunk.documentId().toString())) {
            return true;
        }
        return expectedDocumentIds.isEmpty() && expectedSourceHints.stream().anyMatch(hint -> matchesHint(chunk, hint));
    }

    private int expectedCount(List<String> expectedDocumentIds, List<String> expectedSourceHints) {
        return expectedDocumentIds.isEmpty() ? expectedSourceHints.size() : expectedDocumentIds.size();
    }

    private boolean matchesHint(RetrievedChunk chunk, String hint) {
        String normalizedHint = normalize(hint);
        return normalize(chunk.documentTitle()).contains(normalizedHint)
                || normalize(chunk.chunkText()).contains(normalizedHint)
                || normalize(chunk.excerpt()).contains(normalizedHint);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
