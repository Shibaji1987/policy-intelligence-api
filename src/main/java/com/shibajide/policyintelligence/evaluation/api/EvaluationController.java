package com.shibajide.policyintelligence.evaluation.api;

import com.shibajide.policyintelligence.evaluation.application.EvaluationRun;
import com.shibajide.policyintelligence.evaluation.application.GoldenQuestion;
import com.shibajide.policyintelligence.evaluation.application.RetrievalEvaluationService;
import com.shibajide.policyintelligence.retrieval.application.RetrievalFilters;
import com.shibajide.policyintelligence.security.RetrievalAccessPolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/evaluations")
public class EvaluationController {

    private final RetrievalAccessPolicy accessPolicy;
    private final RetrievalEvaluationService evaluationService;

    public EvaluationController(RetrievalAccessPolicy accessPolicy, RetrievalEvaluationService evaluationService) {
        this.accessPolicy = accessPolicy;
        this.evaluationService = evaluationService;
    }

    @GetMapping("/golden-questions")
    public List<GoldenQuestion> goldenQuestions() {
        return evaluationService.goldenQuestions();
    }

    @PostMapping("/run-golden-questions")
    public EvaluationRun runGoldenQuestions(
            @RequestParam(defaultValue = "default") String tenantId,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String classification
    ) {
        RetrievalFilters filters = accessPolicy.filters(tenantId, department, region, documentType, classification);
        return evaluationService.run(filters);
    }
}
