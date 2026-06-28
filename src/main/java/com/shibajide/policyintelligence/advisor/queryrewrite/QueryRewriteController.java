package com.shibajide.policyintelligence.advisor.queryrewrite;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/advisor/query-rewrite")
public class QueryRewriteController {

    private final QueryRewriteService queryRewriteService;

    public QueryRewriteController(QueryRewriteService queryRewriteService) {
        this.queryRewriteService = queryRewriteService;
    }

    @PostMapping
    public QueryRewriteResult rewrite(@RequestBody QueryRewriteRequest request) {
        return queryRewriteService.rewrite(request);
    }
}
