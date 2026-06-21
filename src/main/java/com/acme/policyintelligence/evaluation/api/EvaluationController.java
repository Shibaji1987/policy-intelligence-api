package com.acme.policyintelligence.evaluation.api;

import com.acme.policyintelligence.advisor.application.AdvisorService;
import com.acme.policyintelligence.evaluation.application.GoldenQuestion;
import com.acme.policyintelligence.evaluation.application.GoldenQuestionResult;
import com.acme.policyintelligence.retrieval.application.RetrievalFilters;
import com.acme.policyintelligence.security.RetrievalAccessPolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/evaluations")
public class EvaluationController {

    private final AdvisorService advisorService;
    private final RetrievalAccessPolicy accessPolicy;

    public EvaluationController(AdvisorService advisorService, RetrievalAccessPolicy accessPolicy) {
        this.advisorService = advisorService;
        this.accessPolicy = accessPolicy;
    }

    @GetMapping("/golden-questions")
    public List<GoldenQuestion> goldenQuestions() {
        return List.of(
                new GoldenQuestion(
                        "contractor-production-access",
                        "Can contractors access production customer data?",
                        List.of("Vendor and Contractor Access", "Production Data Access"),
                        "Contractors require explicit approval and should cite the controlling policy chunk."
                ),
                new GoldenQuestion(
                        "audit-review-cadence",
                        "How often should privileged production access be reviewed?",
                        List.of("Audit and Monitoring", "access review record"),
                        "The answer should include the review cadence and evidence requirement."
                ),
                new GoldenQuestion(
                        "policy-exception-approval",
                        "Who must approve a policy exception?",
                        List.of("Exception Governance", "risk acceptance"),
                        "The answer should identify the approval path and required evidence."
                )
        );
    }

    @PostMapping("/run-golden-questions")
    public List<GoldenQuestionResult> runGoldenQuestions(
            @RequestParam(defaultValue = "default") String tenantId,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String classification
    ) {
        RetrievalFilters filters = accessPolicy.filters(tenantId, department, region, documentType, classification);
        return goldenQuestions().stream()
                .map(question -> evaluate(question, filters))
                .toList();
    }

    private GoldenQuestionResult evaluate(GoldenQuestion goldenQuestion, RetrievalFilters filters) {
        var answer = advisorService.answer(goldenQuestion.question(), filters);
        String evidence = (answer.answer() + " " + answer.sources().stream()
                .map(source -> source.documentTitle() + " " + source.excerpt())
                .reduce("", (left, right) -> left + " " + right)).toLowerCase(Locale.ROOT);
        List<String> matchedHints = goldenQuestion.expectedSourceHints().stream()
                .filter(hint -> evidence.contains(hint.toLowerCase(Locale.ROOT)))
                .toList();
        return new GoldenQuestionResult(
                goldenQuestion.id(),
                goldenQuestion.question(),
                answer.traceId(),
                !matchedHints.isEmpty(),
                matchedHints,
                answer.contextMetrics().usedChunks(),
                answer.qualityPrediction().label(),
                answer.qualityPrediction().probability()
        );
    }
}
