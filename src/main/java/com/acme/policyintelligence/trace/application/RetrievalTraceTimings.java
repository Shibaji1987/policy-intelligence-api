package com.acme.policyintelligence.trace.application;

public record RetrievalTraceTimings(
        long retrievalLatencyMs,
        long contextBuildLatencyMs,
        long llmLatencyMs,
        long mlLatencyMs,
        long totalLatencyMs
) {
}
