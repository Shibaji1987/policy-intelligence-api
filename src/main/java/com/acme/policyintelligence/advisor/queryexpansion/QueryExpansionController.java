package com.acme.policyintelligence.advisor.queryexpansion;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/advisor/query-expansion")
public class QueryExpansionController {

    private final QueryExpansionService queryExpansionService;

    public QueryExpansionController(QueryExpansionService queryExpansionService) {
        this.queryExpansionService = queryExpansionService;
    }

    @PostMapping
    public QueryExpansionResult expand(@RequestBody QueryExpansionRequest request) {
        return queryExpansionService.expand(request);
    }
}
