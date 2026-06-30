package com.shibajide.policyintelligence.evaluation.application;

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
                ),
                new GoldenQuestion(
                        "confusing-contractor-production-customer-data",
                        "Can contractors access production customer data during emergency support?",
                        List.of("Contractor Production Customer Data Access Standard"),
                        "The answer should distinguish contractor production access from employee access and sandbox access.",
                        List.of(),
                        List.of("contractor", "production", "data owner approval", "emergency"),
                        List.of()
                ),
                new GoldenQuestion(
                        "confusing-contractor-sandbox-vs-production",
                        "Can contractors use customer data in a sandbox environment?",
                        List.of("Contractor Sandbox Customer Data Handling Standard"),
                        "The answer should select sandbox masking rules, not production access rules.",
                        List.of(),
                        List.of("sandbox", "masked", "contractor"),
                        List.of()
                ),
                new GoldenQuestion(
                        "confusing-employee-production-vs-contractor",
                        "Do employees need the same approval as contractors for scheduled production customer data access?",
                        List.of("Employee Production Customer Data Access Standard"),
                        "The answer should cite employee scheduled access rules, not contractor emergency access rules.",
                        List.of(),
                        List.of("employee", "scheduled", "manager approval"),
                        List.of()
                )
        );
    }
}
