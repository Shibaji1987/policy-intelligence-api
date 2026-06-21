package com.acme.policyintelligence.trace.api;

import com.acme.policyintelligence.trace.application.RetrievalTraceSummary;
import com.acme.policyintelligence.trace.infrastructure.RetrievalTraceRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
