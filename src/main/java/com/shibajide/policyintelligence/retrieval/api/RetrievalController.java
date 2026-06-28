package com.shibajide.policyintelligence.retrieval.api;

import com.shibajide.policyintelligence.retrieval.application.RetrievalSearchResponse;
import com.shibajide.policyintelligence.retrieval.application.RetrievalSearchService;
import com.shibajide.policyintelligence.security.RetrievalAccessPolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/retrieval")
public class RetrievalController {

    private final RetrievalSearchService retrievalSearchService;
    private final RetrievalAccessPolicy accessPolicy;

    public RetrievalController(RetrievalSearchService retrievalSearchService, RetrievalAccessPolicy accessPolicy) {
        this.retrievalSearchService = retrievalSearchService;
        this.accessPolicy = accessPolicy;
    }

    @GetMapping("/search")
    public RetrievalSearchResponse search(
            @RequestParam String query,
            @RequestParam(required = false) Integer topK,
            @RequestParam(defaultValue = "default") String tenantId,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String classification
    ) {
        return retrievalSearchService.search(
                query,
                topK,
                accessPolicy.filters(tenantId, department, region, documentType, classification)
        );
    }
}
