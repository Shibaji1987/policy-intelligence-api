package com.shibajide.policyintelligence.trace.api;

import com.shibajide.policyintelligence.trace.application.RetrievalTraceDetail;
import com.shibajide.policyintelligence.trace.application.RetrievalFeedbackRequest;
import com.shibajide.policyintelligence.trace.application.RetrievalTraceSummary;
import com.shibajide.policyintelligence.trace.infrastructure.RetrievalTraceRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/retrieval-traces")
public class RetrievalTraceController {

    private final RetrievalTraceRepository retrievalTraceRepository;

    public RetrievalTraceController(RetrievalTraceRepository retrievalTraceRepository) {
        this.retrievalTraceRepository = retrievalTraceRepository;
    }

    @GetMapping
    public List<RetrievalTraceSummary> recent(@RequestParam(defaultValue = "25") int limit) {
        return retrievalTraceRepository.findRecent(Math.clamp(limit, 1, 100));
    }

    @GetMapping("/{traceId}")
    public RetrievalTraceDetail detail(@PathVariable UUID traceId) {
        return retrievalTraceRepository.findDetail(traceId)
                .orElseThrow(() -> new IllegalArgumentException("Retrieval trace not found: " + traceId));
    }

    @PostMapping("/{traceId}/feedback")
    public java.util.Map<String, UUID> feedback(
            @PathVariable UUID traceId,
            @RequestBody RetrievalFeedbackRequest request
    ) {
        return java.util.Map.of("feedbackId", retrievalTraceRepository.saveFeedback(traceId, request.rating(), request.comment()));
    }
}
