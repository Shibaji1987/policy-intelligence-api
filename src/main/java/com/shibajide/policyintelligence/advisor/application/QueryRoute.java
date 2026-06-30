package com.shibajide.policyintelligence.advisor.application;

public record QueryRoute(
        IntentType intent,
        boolean retrievalRequired,
        String reason,
        String response
) {

    public static QueryRoute policyKnowledge() {
        return new QueryRoute(IntentType.POLICY_KNOWLEDGE, true, "POLICY_KNOWLEDGE", "");
    }

    public static QueryRoute noRetrieval(IntentType intent, String reason, String response) {
        return new QueryRoute(intent, false, reason, response);
    }
}
