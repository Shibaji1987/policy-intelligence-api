package com.acme.policyintelligence.evaluation.application;

import com.acme.policyintelligence.retrieval.application.RetrievedChunk;

import java.util.List;

public class EvaluationMetricCalculator {

    public double precisionAt(List<RetrievedChunk> chunks, List<String> expectedDocumentIds, int k) {
        if (chunks.isEmpty() || expectedDocumentIds.isEmpty()) {
            return 0;
        }
        long matches = chunks.stream()
                .limit(k)
                .filter(chunk -> expectedDocumentIds.contains(chunk.documentId().toString()))
                .count();
        return (double) matches / Math.min(k, chunks.size());
    }

    public double recallAt(List<RetrievedChunk> chunks, List<String> expectedDocumentIds, int k) {
        if (expectedDocumentIds.isEmpty()) {
            return 0;
        }
        long matches = chunks.stream()
                .limit(k)
                .map(chunk -> chunk.documentId().toString())
                .distinct()
                .filter(expectedDocumentIds::contains)
                .count();
        return (double) matches / expectedDocumentIds.size();
    }

    public double mrr(List<RetrievedChunk> chunks, List<String> expectedDocumentIds) {
        for (int index = 0; index < chunks.size(); index++) {
            if (expectedDocumentIds.contains(chunks.get(index).documentId().toString())) {
                return 1.0 / (index + 1);
            }
        }
        return 0;
    }
}
