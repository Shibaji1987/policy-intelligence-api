package com.acme.policyintelligence.evaluation.api;

import com.acme.policyintelligence.evaluation.application.GoldenQuestion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/evaluations")
public class EvaluationController {

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
}
