package com.shibajide.policyintelligence.trace.application;

import java.util.List;

public record RetrievalTraceDetail(
        RetrievalTraceSummary summary,
        List<RetrievalTraceSourceSummary> sources
) {
}
