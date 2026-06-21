package com.acme.policyintelligence.retrieval.api;

import com.acme.policyintelligence.retrieval.application.RetrievalSearchResponse;
import com.acme.policyintelligence.retrieval.application.RetrievalSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/retrieval")
public class RetrievalController {

    private final RetrievalSearchService retrievalSearchService;

    public RetrievalController(RetrievalSearchService retrievalSearchService) {
        this.retrievalSearchService = retrievalSearchService;
    }

    @GetMapping("/search")
    public RetrievalSearchResponse search(
            @RequestParam String query,
            @RequestParam(required = false) Integer topK
    ) {
        return retrievalSearchService.search(query, topK);
    }
}
