package com.shibajide.policyintelligence.advisor.application;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Component
public class RuleBasedQueryRouter implements QueryRouter {

    private static final Set<String> CONVERSATIONAL_PHRASES = Set.of(
            "hi",
            "hello",
            "hey",
            "good morning",
            "good afternoon",
            "good evening",
            "thanks",
            "thank you",
            "ok",
            "okay",
            "cool",
            "bye",
            "goodbye"
    );

    private static final Set<String> POLICY_DOMAIN_TERMS = Set.of(
            "access",
            "approval",
            "approve",
            "approves",
            "exception",
            "exceptions",
            "evidence",
            "policy",
            "policies",
            "procedure",
            "procedures",
            "standard",
            "standards",
            "control",
            "controls",
            "compliance",
            "audit",
            "risk",
            "security",
            "privacy",
            "retention",
            "classification",
            "classified",
            "data",
            "customer",
            "production",
            "sandbox",
            "contractor",
            "contractors",
            "vendor",
            "vendors",
            "employee",
            "employees",
            "privileged",
            "credential",
            "credentials",
            "incident",
            "emergency",
            "ticket",
            "owner",
            "reviewer",
            "sign-off",
            "mfa",
            "password",
            "encryption",
            "pii",
            "hr",
            "leave",
            "vacation",
            "benefits",
            "remote",
            "work",
            "dress",
            "document",
            "documents",
            "source",
            "sources"
    );

    private static final Set<String> FOLLOW_UP_PHRASES = Set.of(
            "what about it",
            "what about that",
            "what about them",
            "tell me more",
            "explain more",
            "can you expand",
            "can you clarify"
    );

    private static final Set<String> GREETING_WORDS = Set.of("hi", "hello", "hey");

    @Override
    public QueryRoute route(String question) {
        String normalized = normalize(question);
        if (normalized.isBlank()) {
            return QueryRoute.noRetrieval(
                    IntentType.EMPTY,
                    "EMPTY_QUERY",
                    "Ask a policy question and I will answer using only retrieved policy sources."
            );
        }
        if (isChitchat(normalized)) {
            return QueryRoute.noRetrieval(
                    IntentType.CHITCHAT,
                    "CHITCHAT",
                    "Hi. Ask me a policy question and I will answer using only retrieved policy sources."
            );
        }
        if (normalized.length() < 4 && !normalized.contains("?")) {
            return QueryRoute.noRetrieval(
                    IntentType.TOO_SHORT,
                    "TOO_SHORT_FOR_RETRIEVAL",
                    "Please ask a specific policy question so I can retrieve the right source evidence."
            );
        }
        if (isClarification(normalized) && !hasPolicyDomainSignal(normalized)) {
            return QueryRoute.noRetrieval(
                    IntentType.CLARIFICATION,
                    "CLARIFICATION_NEEDS_POLICY_TOPIC",
                    "Please include the policy topic you want clarified so I can retrieve the right source evidence."
            );
        }
        if (!hasPolicyDomainSignal(normalized)) {
            return QueryRoute.noRetrieval(
                    IntentType.OUT_OF_SCOPE,
                    "OUT_OF_SCOPE_QUERY",
                    "I can only answer policy questions from retrieved policy sources. Please ask about a policy, control, approval, evidence, access, data handling, HR, or compliance topic."
            );
        }
        return QueryRoute.policyKnowledge();
    }

    private boolean isChitchat(String normalized) {
        if (CONVERSATIONAL_PHRASES.contains(normalized)) {
            return true;
        }
        if (normalized.matches("^(hi|hello|hey)\\b.*")) {
            return true;
        }
        if (normalized.matches(".*\\b(how are you|how you are doing|hows it going|how's it going|what's up|whats up)\\b.*")) {
            return true;
        }
        String firstWord = normalized.split(" ", 2)[0];
        return GREETING_WORDS.stream().anyMatch(greeting -> editDistance(firstWord, greeting) <= 1);
    }

    private boolean isClarification(String normalized) {
        return FOLLOW_UP_PHRASES.stream().anyMatch(normalized::contains);
    }

    private boolean hasPolicyDomainSignal(String normalized) {
        return Arrays.stream(normalized.split(" "))
                .map(word -> word.replaceAll("[^a-z0-9-]", ""))
                .anyMatch(POLICY_DOMAIN_TERMS::contains);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value
                .strip()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[!.,;:]+$", "")
                .replaceAll("\\s+", " ");
    }

    private int editDistance(String left, String right) {
        int[][] distance = new int[left.length() + 1][right.length() + 1];
        for (int i = 0; i <= left.length(); i++) {
            distance[i][0] = i;
        }
        for (int j = 0; j <= right.length(); j++) {
            distance[0][j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                distance[i][j] = Math.min(
                        Math.min(distance[i - 1][j] + 1, distance[i][j - 1] + 1),
                        distance[i - 1][j - 1] + cost
                );
            }
        }
        return distance[left.length()][right.length()];
    }
}
