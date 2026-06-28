package com.shibajide.policyintelligence.trace.application;

public record RetrievalTraceTimings(
        long retrievalLatencyMs,
        long contextBuildLatencyMs,
        long llmLatencyMs,
        long mlLatencyMs,
        long totalLatencyMs
) {
}
