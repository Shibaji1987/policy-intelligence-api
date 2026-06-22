package com.acme.policyintelligence.evaluation.application;

import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class GoldenQuestionRepository {

    public List<GoldenQuestion> findAll() {
        return List.of(
                new GoldenQuestion(
                        "contractor-production-access",
                        "Can contractors access production customer data?",
                        List.of("Vendor and Contractor Access", "Production Data Access"),
                        "Contractors require explicit approval and should cite the controlling policy chunk.",
                        List.of(),
                        List.of("contractor", "approval", "production"),
                        List.of()
                ),
                new GoldenQuestion(
                        "audit-review-cadence",
                        "How often should privileged production access be reviewed?",
                        List.of("Audit and Monitoring", "access review record"),
                        "The answer should include the review cadence and evidence requirement.",
                        List.of(),
                        List.of("review", "evidence", "access"),
                        List.of()
                ),
                new GoldenQuestion(
                        "policy-exception-approval",
                        "Who must approve a policy exception?",
                        List.of("Exception Governance", "risk acceptance"),
                        "The answer should identify the approval path and required evidence.",
                        List.of(),
                        List.of("approval", "exception", "evidence"),
                        List.of()
                )
        );
    }
}
